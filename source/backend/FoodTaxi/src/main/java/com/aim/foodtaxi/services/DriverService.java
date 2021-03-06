package com.aim.foodtaxi.services;

import java.util.Date;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aim.foodtaxi.domain.DriverEntity;
import com.aim.foodtaxi.dto.Driver;
import com.aim.foodtaxi.enums.DriverAccountStatus;
import com.aim.foodtaxi.mappers.DriverMapper;
import com.aim.foodtaxi.repositories.DriverRepository;

@Service
@Transactional(readOnly=true)
public class DriverService {

    @Autowired
    private DriverRepository driverRepository;

    @Autowired
    private DriverMapper driverMapper;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Transactional(readOnly=false)
    public HttpStatus createDriver(Driver driver) {
        Optional<DriverEntity> existingUser = driverRepository.findOneByUsername(driver.getUsername());
        if (existingUser.isPresent()) {
            return HttpStatus.CONFLICT;
        }
        DriverEntity driverEntity = driverMapper.driverToDriverEntity(driver);
        driverEntity.setRegisterDate(new Date());
        driverEntity.setRating(5);
        //TODO: Maybe first driver account status will be awaiting
        driverEntity.setStatus(DriverAccountStatus.ACTIVE);
        //TODO: Add password encryption at some point
        driverEntity.setPassword(bCryptPasswordEncoder.encode(driverEntity.getPassword()));
        driverRepository.save(driverEntity);
        return HttpStatus.CREATED;
    }

	public Driver getDriver(Long driverId) {
		DriverEntity entity = driverRepository.findOne(driverId);
		Driver driver = driverMapper.driverEntityToDriver(entity);
		return driver;
	}

    public Driver getDriverByUsername(String username) {
        Optional<DriverEntity> entity = driverRepository.findOneByUsername(username);
        if (entity.isPresent()) {
            Driver driver = driverMapper.driverEntityToDriver(entity.get());
            return driver;
        }
        return null;
    }
    
    @Transactional(readOnly=false)
    public void updateLocation(String username, double lat, double lng){
    	Optional<DriverEntity> entity = driverRepository.findOneByUsername(username);
    	
    	if(!entity.isPresent()){
    		throw new RuntimeException("No driver with username: "+username);
    	}
    	DriverEntity driver = entity.get();
    	driver.setLatitude(lat);
    	driver.setLongitude(lng);
    	driver.setLastLocationUpdate(new Date());
    	driverRepository.save(driver);
    }
}
