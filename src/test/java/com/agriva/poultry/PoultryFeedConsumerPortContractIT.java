package com.agriva.poultry;

import com.agriva.feeding.FeedConsumerPortContract;
import com.agriva.feeding.port.FeedConsumerPort;
import com.agriva.poultry.adapter.PoultryFeedAdapter;
import com.agriva.poultry.model.dto.request.PoultryBatchRequest;
import com.agriva.poultry.model.dto.request.PoultryHouseRequest;
import com.agriva.poultry.model.dto.response.PoultryHouseResponse;
import com.agriva.poultry.model.enums.PoultrySpecies;
import com.agriva.poultry.service.PoultryBatchService;
import com.agriva.poultry.service.PoultryHouseService;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.UUID;

class PoultryFeedConsumerPortContractIT extends FeedConsumerPortContract {

    @Autowired PoultryFeedAdapter adapter;
    @Autowired PoultryHouseService houseService;
    @Autowired PoultryBatchService batchService;

    @Override
    protected FeedConsumerPort port() {
        return adapter;
    }

    @Override
    protected FeedConsumerPortContract.ConsumerFixture createLiveConsumer(UUID farmId, UUID userId) {
        PoultryHouseRequest hr = new PoultryHouseRequest();
        hr.setName("Contract House " + UUID.randomUUID());
        PoultryHouseResponse house = houseService.createHouse(hr, farmId, userId);

        PoultryBatchRequest br = new PoultryBatchRequest();
        br.setSpecies(PoultrySpecies.BROILER);
        br.setInitialCount(1000L);
        br.setArrivalDate(LocalDate.now());
        br.setStage("GROWER");
        batchService.createBatch(house.getId(), br, farmId, userId);

        String expectedDisplayName = house.getName() + " – BROILER";
        return new FeedConsumerPortContract.ConsumerFixture(
                house.getId(), expectedDisplayName, 1000L);
    }

    @Override
    protected void softDeleteConsumer(UUID consumerId, UUID farmId) {
        houseService.deleteHouse(consumerId, farmId);
    }
}