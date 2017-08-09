package com.aim.foodtaxi.services;

import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.aim.foodtaxi.domain.BidEntity;
import com.aim.foodtaxi.domain.DeliveryEntity;
import com.aim.foodtaxi.domain.DriverEntity;
import com.aim.foodtaxi.dto.Bid;
import com.aim.foodtaxi.mappers.BidMapper;
import com.aim.foodtaxi.repositories.BidRepository;
import com.aim.foodtaxi.repositories.DeliveryRepository;
import com.aim.foodtaxi.repositories.DriverRepository;

@Service
@Transactional
public class BidService {
	
    @Autowired
    private BidRepository bidRepository;
    
    @Autowired
    private DeliveryRepository deliveryRepository;
    
    @Autowired
    private DriverRepository driverRepository;
    
    @Autowired
    private BidMapper bidMapper;

    public boolean createBid(Bid bid, Long deliveryId, String driverUsername) {
        BidEntity bidEntity = bidMapper.bidToBidEntity(bid);
        Optional<DeliveryEntity> deliveryEntity = deliveryRepository.findOneById(deliveryId);
        Optional<DriverEntity> driverEntity = driverRepository.findOneByUsername(driverUsername);
        if (!driverEntity.isPresent() || !deliveryEntity.isPresent()) {
            return false;
        }
        
        bidEntity.setDriver(driverEntity.get());
        bidEntity.setDelivery(deliveryEntity.get());


        BidEntity savedEntity = bidRepository.save(bidEntity);
        if (deliveryEntity.get().getBestBid() == null || 
                deliveryEntity.get().getBestBid().getPrice() > bidEntity.getPrice()) {
            deliveryEntity.get().setBestBid(savedEntity);
            deliveryRepository.save(deliveryEntity.get());
        }
        return true;
    }
}
