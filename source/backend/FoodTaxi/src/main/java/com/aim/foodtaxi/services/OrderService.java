package com.aim.foodtaxi.services;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import javax.persistence.EntityNotFoundException;

import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aim.foodtaxi.domain.BrandEntity;
import com.aim.foodtaxi.domain.ClientEntity;
import com.aim.foodtaxi.domain.DeliveryEntity;
import com.aim.foodtaxi.domain.OrderEntity;
import com.aim.foodtaxi.domain.ShopEntity;
import com.aim.foodtaxi.dto.CreateOrder;
import com.aim.foodtaxi.dto.Order;
import com.aim.foodtaxi.enums.DeliveryStatus;
import com.aim.foodtaxi.enums.OrderStatus;
import com.aim.foodtaxi.mappers.OrderMapper;
import com.aim.foodtaxi.repositories.BrandRepository;
import com.aim.foodtaxi.repositories.ClientRepository;
import com.aim.foodtaxi.repositories.DeliveryRepository;
import com.aim.foodtaxi.repositories.OrderRepository;
import com.aim.foodtaxi.repositories.ShopRepository;

@Service
@Transactional
public class OrderService {

	private final static short BID_MINUTES = 5;
	private final static int DEFAULT_DELIVERY_COMPLETION = 15;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private BrandRepository brandRepository;

	@Autowired
	private ClientRepository clientRepository;

	@Autowired
	private DeliveryRepository deliveryRepository;

	@Autowired
	private ShopRepository shopRepository;

	@Autowired
	private OrderMapper orderMapper;

	@Autowired
	private SchedulerService schedulerService;

	public void createOrder(CreateOrder order) throws EntityNotFoundException {

		Optional<ClientEntity> clientEntity = clientRepository.findOneById(order.getClientId());
		if (!clientEntity.isPresent()) {
			throw new EntityNotFoundException();
		}
		Optional<BrandEntity> brandEntity = brandRepository.findOneById(order.getBrandId());
		if (!brandEntity.isPresent()) {
			throw new EntityNotFoundException();
		}
		OrderEntity orderEntity = generateOrder(order, clientEntity.get(), brandEntity.get());
		orderEntity = orderRepository.save(orderEntity);
		DeliveryEntity deliveryEntity = generateDelivery(order, orderEntity);
		deliveryEntity = deliveryRepository.save(deliveryEntity);
	}

	private OrderEntity generateOrder(CreateOrder order, ClientEntity clientEntity, BrandEntity brandEntity) {
		OrderEntity orderEntity = new OrderEntity();
		orderEntity.setItemDescription(order.getItemDescription());
		orderEntity.setClient(clientEntity);
		orderEntity.setBrand(brandEntity);
		orderEntity.setOrderDate(new Date());
		orderEntity.setDueDate(order.getDueDate());
		orderEntity.setStatus(OrderStatus.AWAITING_CONFIRMATION);
		orderEntity.setOrderValue(order.getOrderValue());
		orderEntity.setShop(findBestShop(order, brandEntity));
		orderEntity.setCustomerName(order.getCustName());
		orderEntity.setCustomerEmail(order.getCustEmail());
		orderEntity.setCustomerPhone(order.getCustPhone());
		return orderEntity;
	}

	private DeliveryEntity generateDelivery(CreateOrder order, OrderEntity orderEntity) {
		DeliveryEntity delivery = new DeliveryEntity();
		delivery.setCodAmount(order.getCodAmount());
		delivery.setDescription(order.getItemDescription());
		delivery.setDueDate(order.getDueDate());
		delivery.setEndAddressText(order.getAddress());
		delivery.setEndLatitude(order.getLatitude());
		delivery.setEndLongtitude(order.getLongtitude());
		delivery.setHasCod(order.isHasCod());
		delivery.setOrder(orderEntity);
		delivery.setOrderValue(order.getOrderValue());
		delivery.setPin(generatePin());
		delivery.setStartAddressText(orderEntity.getShop().getAddressText());
		delivery.setStartLatitude(orderEntity.getShop().getLatitude());
		delivery.setStartLongtitude(orderEntity.getShop().getLongtitude());
		delivery.setStatus(DeliveryStatus.NEW);
		return delivery;
	}

	private String generatePin() {
		int minimum = 1000;
		Random randomGenerator = new Random();
		int nextInt = randomGenerator.nextInt(8999);
		return String.valueOf(minimum + nextInt);
	}

	// This method returns the shop which is closest to the order per brand
	private ShopEntity findBestShop(CreateOrder order, BrandEntity brand) {
		ShopEntity bestShop = null;
		Double currentBestDistance = null;
		for (ShopEntity shop : brand.getShops()) {
			Double latitude = order.getLatitude() - shop.getLatitude();
			latitude = latitude * latitude;
			Double longtitude = order.getLongtitude() - shop.getLongtitude();
			longtitude = longtitude * longtitude;

			if (bestShop == null || currentBestDistance > longtitude + latitude) {
				bestShop = shop;
				currentBestDistance = longtitude + latitude;
			}
		}
		return bestShop;
	}

	public List<Order> getShopOrders(long shopId) {

		ShopEntity shop = shopRepository.findOne(shopId);
		if (shop == null) {
			throw new RuntimeException("No shop found for id: " + shopId);
		}
		List<OrderEntity> orders = new ArrayList<>();
		orders.addAll(orderRepository.getAllByShopAndStatusIn(shop,
				Arrays.asList(OrderStatus.AWAITING_CONFIRMATION, OrderStatus.CONFIRMED)));
		List<Order> resp = new ArrayList<>();

		if (orders != null && !orders.isEmpty()) {
			resp = orderMapper.orderEntitiesToOrders(orders);
		}

		return resp;
	}

	public void confirmOrder(Long orderId, boolean confirmed, Integer completionMinutes) {
		OrderEntity order = orderRepository.getOne(orderId);
		if (confirmed) {
			order.setStatus(OrderStatus.CONFIRMED);
			DeliveryEntity delivery = order.getDelivery();
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.MINUTE, DEFAULT_DELIVERY_COMPLETION);
			if (completionMinutes != null && completionMinutes > 0) {
				cal.add(Calendar.MINUTE, completionMinutes);
			}
			Date pickUp = cal.getTime();
			order.setPickupDate(pickUp);
			delivery.setPickupDueDate(pickUp);
			delivery.setStatus(DeliveryStatus.BIDDING);
			delivery.setExpectedBidEnd(
					Date.from(LocalDateTime.now().plusMinutes(BID_MINUTES).atZone(ZoneId.systemDefault()).toInstant()));
			deliveryRepository.save(delivery);
			orderRepository.save(order);
			try {
				schedulerService.scheduleBidExpiration(delivery.getId(), BID_MINUTES);
			} catch (SchedulerException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		} else {
			// TODO find new shop
		}
	}

	@Transactional(readOnly = false)
	public void handOver(long orderId) {
		OrderEntity order = orderRepository.getOne(orderId);
		DeliveryEntity delivery = order.getDelivery();
		delivery.setStatus(DeliveryStatus.DELIVERY);
		delivery.setPickedupDate(new Date());
		order.setStatus(OrderStatus.IN_DELIVERY);
		deliveryRepository.save(delivery);
		orderRepository.save(order);
	}
}
