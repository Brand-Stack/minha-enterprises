package com.app.billing.service;

import com.app.billing.dao.SmallClientRepository;
import com.app.billing.dto.SmallClientDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceAlreadyExistsException;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.SmallClient;
import com.app.billing.model.SmallClientEntry;
import com.app.billing.model.SmallClientEntryQuotation;
import com.app.billing.util.AuditUtil;
import com.app.billing.util.CodeGenerator;
import com.app.billing.util.PaginationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class SmallClientService {

    private final SmallClientRepository smallClientRepository;
    private final CodeGenerator codeGenerator;
    private final AuditUtil auditUtil;
    private final MongoTemplate mongoTemplate;

    @Transactional
    public SmallClientDto create(SmallClientDto dto) {
        if (dto.getPartyType() == null) {
            dto.setPartyType(SmallClient.SmallClientType.CUSTOMER);
        }

        String manualCode = dto.getPartyCode() != null ? dto.getPartyCode().trim() : "";
        if (StringUtils.hasText(manualCode)) {
            if (smallClientRepository.existsByPartyCode(manualCode)) {
                throw new ResourceAlreadyExistsException("Small client with code " + manualCode + " already exists");
            }
            dto.setPartyCode(manualCode);
        } else {
            String prefix = (dto.getPartyType() == SmallClient.SmallClientType.CUSTOMER) ? "SC"
                    : (dto.getPartyType() == SmallClient.SmallClientType.SUPPLIER) ? "SS" : "SB";
            String lastCode = findLastSmallClientCode(dto.getPartyType(), prefix);
            String nextCode = codeGenerator.generateSmallClientCode(dto.getPartyType(), lastCode);
            while (smallClientRepository.existsByPartyCode(nextCode)) {
                nextCode = codeGenerator.generateSmallClientCode(dto.getPartyType(), nextCode);
            }
            dto.setPartyCode(nextCode);
        }

        SmallClient client = toEntity(dto);
        auditUtil.setCreatedBy(client);
        client = smallClientRepository.save(client);
        return toDto(client);
    }

    @Transactional
    public SmallClientDto update(String id, SmallClientDto dto) {
        SmallClient client = smallClientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Small client not found with id: " + id));

        if (dto.getPartyType() == null) {
            dto.setPartyType(client.getPartyType());
        }

        dto.setPartyCode(client.getPartyCode());

        String oldName = client.getPartyName();
        updateEntity(client, dto);
        auditUtil.setUpdatedBy(client);
        client = smallClientRepository.save(client);

        try {
            Query byCustomer = new Query(Criteria.where("customerId").is(id));
            List<SmallClientEntryQuotation> monthlyList = mongoTemplate.find(byCustomer, SmallClientEntryQuotation.class);
            List<String> monthlyIds = new ArrayList<>();
            for (SmallClientEntryQuotation m : monthlyList) {
                monthlyIds.add(m.getId());
                String title = client.getPartyName() + " - " + m.getMonth() + " " + m.getYear();
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("id").is(m.getId())),
                        new Update().set("customerName", client.getPartyName()).set("title", title),
                        SmallClientEntryQuotation.class);
            }
            if (oldName != null && !oldName.isBlank()
                    && !oldName.equals(client.getPartyName())
                    && !monthlyIds.isEmpty()) {
                Query entryQ = new Query(Criteria.where("monthlyQuotationId").in(monthlyIds)
                        .and("consignor").is(oldName));
                mongoTemplate.updateMulti(entryQ, new Update().set("consignor", client.getPartyName()),
                        SmallClientEntry.class);
            }
        } catch (Exception e) {
            log.error("Failed to sync small client name to entries: {}", e.getMessage());
        }

        return toDto(client);
    }

    public PageResponse<SmallClientDto> findByPartyType(SmallClient.SmallClientType partyType, int page, int size, String sortBy, String sortDir) {
        List<SmallClient> list = smallClientRepository.findByPartyType(partyType);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), list.size());
        List<SmallClient> paged = list.subList(start, end);

        return PaginationUtil.toPageResponse(
                paged.stream().map(this::toDto).collect(Collectors.toList()),
                page,
                size,
                list.size()
        );
    }

    public List<SmallClientDto> findAllByPartyType(SmallClient.SmallClientType partyType) {
        return smallClientRepository.findByPartyType(partyType)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<SmallClientDto> findAllForPurchase() {
        return smallClientRepository.findSmallClientsForPurchase()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public SmallClientDto findById(String id) {
        SmallClient client = smallClientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Small client not found with id: " + id));
        return toDto(client);
    }

    public PageResponse<SmallClientDto> findAll(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        Page<SmallClient> pageResult = smallClientRepository.findAll(pageable);
        return PaginationUtil.toPageResponse(pageResult.map(this::toDto));
    }

    public PageResponse<SmallClientDto> search(String searchTerm, int page, int size, String sortBy, String sortDir) {
        String term = searchTerm != null ? searchTerm.trim() : "";
        String pattern = term.isEmpty() ? ".*" : ".*" + Pattern.quote(term) + ".*";
        List<SmallClient> list = smallClientRepository.searchSmallClients(pattern);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), list.size());
        List<SmallClient> paged = list.subList(start, end);

        return PaginationUtil.toPageResponse(
                paged.stream().map(this::toDto).collect(Collectors.toList()),
                page,
                size,
                list.size()
        );
    }

    public List<SmallClientDto> findAllForExport() {
        return smallClientRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void delete(String id) {
        if (!smallClientRepository.existsById(id)) {
            throw new ResourceNotFoundException("Small client not found with id: " + id);
        }
        smallClientRepository.deleteById(id);
    }

    private String findLastSmallClientCode(SmallClient.SmallClientType partyType, String prefix) {
        return smallClientRepository.findAll().stream()
                .filter(p -> p.getPartyType() == partyType
                        && p.getPartyCode() != null
                        && p.getPartyCode().startsWith(prefix))
                .map(SmallClient::getPartyCode)
                .max(Comparator.comparingInt(code -> {
                    try {
                        return Integer.parseInt(code.substring(prefix.length()));
                    } catch (Exception e) {
                        return 0;
                    }
                }))
                .orElse(null);
    }

    private SmallClient toEntity(SmallClientDto dto) {
        return SmallClient.builder()
                .partyCode(dto.getPartyCode())
                .partyName(dto.getPartyName())
                .contactPerson(dto.getContactPerson())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .whatsappNumber(dto.getWhatsappNumber())
                .address(dto.getAddress())
                .city(dto.getCity())
                .state(dto.getState())
                .pincode(dto.getPincode())
                .gstin(dto.getGstin())
                .partyType(dto.getPartyType())
                .build();
    }

    private SmallClientDto toDto(SmallClient client) {
        SmallClientDto dto = new SmallClientDto();
        dto.setId(client.getId());
        dto.setPartyCode(client.getPartyCode());
        dto.setPartyName(client.getPartyName());
        dto.setContactPerson(client.getContactPerson());
        dto.setEmail(client.getEmail());
        dto.setPhone(client.getPhone());
        dto.setWhatsappNumber(client.getWhatsappNumber());
        dto.setAddress(client.getAddress());
        dto.setCity(client.getCity());
        dto.setState(client.getState());
        dto.setPincode(client.getPincode());
        dto.setGstin(client.getGstin());
        dto.setPartyType(client.getPartyType());
        dto.setLastUpdatedBy(client.getLastUpdatedBy());
        return dto;
    }

    private void updateEntity(SmallClient client, SmallClientDto dto) {
        client.setPartyName(dto.getPartyName());
        client.setContactPerson(dto.getContactPerson());
        client.setEmail(dto.getEmail());
        client.setPhone(dto.getPhone());
        client.setWhatsappNumber(dto.getWhatsappNumber());
        client.setAddress(dto.getAddress());
        client.setCity(dto.getCity());
        client.setState(dto.getState());
        client.setPincode(dto.getPincode());
        client.setGstin(dto.getGstin());
        client.setPartyType(dto.getPartyType());
    }
}
