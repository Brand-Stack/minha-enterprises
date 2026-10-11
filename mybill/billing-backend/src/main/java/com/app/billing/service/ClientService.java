package com.app.billing.service;

import com.app.billing.dao.ClientRepository;
import com.app.billing.dto.ClientDto;
import com.app.billing.dto.PageResponse;
import com.app.billing.exception.ResourceAlreadyExistsException;
import com.app.billing.exception.ResourceNotFoundException;
import com.app.billing.model.Client;
import com.app.billing.model.CourierQuotation;
import com.app.billing.model.MonthlyCourierEntry;
import com.app.billing.model.MonthlyCourierQuotation;
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
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;
    private final CodeGenerator codeGenerator;
    private final AuditUtil auditUtil;
    private final MongoTemplate mongoTemplate;

    @Transactional
    public ClientDto create(ClientDto dto) {
        if (dto.getPartyType() == null) {
            throw new IllegalArgumentException("Client type is required. Must be CUSTOMER, SUPPLIER, or BOTH.");
        }

        String prefix = (dto.getPartyType() == Client.ClientType.CUSTOMER) ? "CU"
                : (dto.getPartyType() == Client.ClientType.SUPPLIER) ? "SU" : "BT";

        String lastCode = clientRepository.findAll().stream()
                .filter(p -> p.getPartyType() == dto.getPartyType()
                        && p.getPartyCode() != null
                        && p.getPartyCode().startsWith(prefix))
                .map(Client::getPartyCode)
                .max(String::compareTo)
                .orElse(null);

        dto.setPartyCode(codeGenerator.generateClientCode(dto.getPartyType(), lastCode));

        if (clientRepository.existsByPartyCode(dto.getPartyCode())) {
            throw new ResourceAlreadyExistsException("Client with code " + dto.getPartyCode() + " already exists");
        }

        Client client = toEntity(dto);
        auditUtil.setCreatedBy(client);
        client = clientRepository.save(client);
        return toDto(client);
    }

    @Transactional
    public ClientDto update(String id, ClientDto dto) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + id));

        if (dto.getPartyType() == null) {
            throw new IllegalArgumentException("Client type is required. Must be CUSTOMER, SUPPLIER, or BOTH.");
        }

        dto.setPartyCode(client.getPartyCode());

        String oldName = client.getPartyName();
        updateEntity(client, dto);
        auditUtil.setUpdatedBy(client);
        client = clientRepository.save(client);

        try {
            Query byCustomer = new Query(Criteria.where("customerId").is(id));
            mongoTemplate.updateMulti(byCustomer, new Update().set("customerName", client.getPartyName()),
                    CourierQuotation.class);

            List<MonthlyCourierQuotation> monthlyList = mongoTemplate.find(byCustomer, MonthlyCourierQuotation.class);
            List<String> monthlyIds = new ArrayList<>();
            for (MonthlyCourierQuotation m : monthlyList) {
                monthlyIds.add(m.getId());
                String title = client.getPartyName() + " - " + m.getMonth() + " " + m.getYear();
                mongoTemplate.updateFirst(
                        Query.query(Criteria.where("id").is(m.getId())),
                        new Update().set("customerName", client.getPartyName()).set("title", title),
                        MonthlyCourierQuotation.class);
            }
            if (oldName != null && !oldName.isBlank()
                    && !oldName.equals(client.getPartyName())
                    && !monthlyIds.isEmpty()) {
                Query entryQ = new Query(Criteria.where("monthlyQuotationId").in(monthlyIds)
                        .and("consignor").is(oldName));
                mongoTemplate.updateMulti(entryQ, new Update().set("consignor", client.getPartyName()),
                        MonthlyCourierEntry.class);
            }
        } catch (Exception e) {
            log.error("Failed to sync client name to courier quotations: {}", e.getMessage());
        }

        return toDto(client);
    }

    public PageResponse<ClientDto> findByPartyType(Client.ClientType partyType, int page, int size, String sortBy, String sortDir) {
        List<Client> list = clientRepository.findByPartyType(partyType);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), list.size());
        List<Client> paged = list.subList(start, end);

        return PaginationUtil.toPageResponse(
                paged.stream().map(this::toDto).collect(Collectors.toList()),
                page,
                size,
                list.size()
        );
    }

    public List<ClientDto> findAllByPartyType(Client.ClientType partyType) {
        return clientRepository.findByPartyType(partyType)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public List<ClientDto> findAllForPurchase() {
        return clientRepository.findClientsForPurchase()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public ClientDto findById(String id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found with id: " + id));
        return toDto(client);
    }

    public PageResponse<ClientDto> findAll(int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);
        Page<Client> pageResult = clientRepository.findAll(pageable);
        return PaginationUtil.toPageResponse(pageResult.map(this::toDto));
    }

    public PageResponse<ClientDto> search(String searchTerm, int page, int size, String sortBy, String sortDir) {
        String term = searchTerm != null ? searchTerm.trim() : "";
        String pattern = term.isEmpty() ? ".*" : ".*" + Pattern.quote(term) + ".*";
        List<Client> list = clientRepository.searchClients(pattern);
        Pageable pageable = PaginationUtil.createPageable(page, size, sortBy, sortDir);

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), list.size());
        List<Client> paged = list.subList(start, end);

        return PaginationUtil.toPageResponse(
                paged.stream().map(this::toDto).collect(Collectors.toList()),
                page,
                size,
                list.size()
        );
    }

    public List<ClientDto> findAllForExport() {
        return clientRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void delete(String id) {
        if (!clientRepository.existsById(id)) {
            throw new ResourceNotFoundException("Client not found with id: " + id);
        }
        clientRepository.deleteById(id);
    }

    private Client toEntity(ClientDto dto) {
        return Client.builder()
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

    private ClientDto toDto(Client client) {
        ClientDto dto = new ClientDto();
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

    private void updateEntity(Client client, ClientDto dto) {
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
