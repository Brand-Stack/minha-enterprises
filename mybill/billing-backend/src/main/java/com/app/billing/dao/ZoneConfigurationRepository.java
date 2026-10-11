package com.app.billing.dao;

import com.app.billing.model.ZoneConfiguration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ZoneConfigurationRepository extends MongoRepository<ZoneConfiguration, String> {

    @Query("{ 'isDeleted' : { $ne: true } }")
    List<ZoneConfiguration> findByIsDeletedFalse();

    @Query("{ 'isDeleted' : { $ne: true } }")
    Page<ZoneConfiguration> findByIsDeletedFalse(Pageable pageable);

    @Query("{ 'isDeleted' : { $ne: true }, 'isActive' : true }")
    List<ZoneConfiguration> findActiveZones();

    @Query("{ 'isDeleted' : { $ne: true }, 'isActive' : true, 'zoneType' : ?0 }")
    List<ZoneConfiguration> findActiveZonesByZoneType(String zoneType);

    @Query("{ 'isDeleted' : { $ne: true }, 'zoneName' : { $regex: ?0, $options: 'i' } }")
    Page<ZoneConfiguration> searchZones(String searchStr, Pageable pageable);
}
