package com.b201.ecovillage.property.application;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;
import com.b201.ecovillage.identity.repository.UserAccountRepository;
import com.b201.ecovillage.ledger.application.AtomicLedgerTransactionService;
import com.b201.ecovillage.ledger.application.LedgerDomainMutation;
import com.b201.ecovillage.ledger.application.LedgerTransactionCommand;
import com.b201.ecovillage.ledger.application.LedgerTransactionPosting;
import com.b201.ecovillage.ledger.domain.LedgerAccount;
import com.b201.ecovillage.ledger.domain.LedgerAccountStatus;
import com.b201.ecovillage.ledger.domain.LedgerAccountType;
import com.b201.ecovillage.ledger.domain.LedgerActorType;
import com.b201.ecovillage.ledger.repository.LedgerAccountRepository;

/**
 * Owns classroom-seat property state and keeps every monetary operation in the
 * double-entry ledger. The teacher's initial ownership is represented by the
 * class-level PROPERTY_REVENUE account; student owners receive rent directly
 * in their wallet.
 */
@Service
public class PropertyService {

    private static final String PROPERTY_REVENUE = "PROPERTY_REVENUE";
    private static final String SUBSCRIPTION_LIABILITY = "SUBSCRIPTION_LIABILITY";

    private final ClassroomAuthorizationService authorization;
    private final ClassroomModuleRepository modules;
    private final UserAccountRepository users;
    private final LedgerAccountRepository ledger;
    private final AtomicLedgerTransactionService transactions;
    private final JdbcTemplate jdbc;
    private final UuidV7Generator uuids;
    private final Clock clock;

    public PropertyService(
        ClassroomAuthorizationService authorization,
        ClassroomModuleRepository modules,
        UserAccountRepository users,
        LedgerAccountRepository ledger,
        AtomicLedgerTransactionService transactions,
        JdbcTemplate jdbc,
        UuidV7Generator uuids,
        Clock clock
    ) {
        this.authorization = authorization;
        this.modules = modules;
        this.users = users;
        this.ledger = ledger;
        this.transactions = transactions;
        this.jdbc = jdbc;
        this.uuids = uuids;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> layout(String subject, UUID classroomId) {
        authorizeRead(subject, classroomId);
        requireModule(classroomId, "REAL_ESTATE");
        Map<String, Object> result = new LinkedHashMap<>();
        query("select row_count,column_count,state_version from property_layouts where classroom_id=?", rs -> {
            if (rs.next()) {
                result.put("row_count", rs.getInt(1));
                result.put("column_count", rs.getInt(2));
                result.put("state_version", rs.getLong(3));
            } else {
                result.put("row_count", 0);
                result.put("column_count", 0);
                result.put("state_version", 0L);
            }
            return null;
        }, classroomId);
        // A teacher needs the hidden/inactive cells to edit the layout. Students
        // must never receive removed or private cells in the seat map.
        result.put("properties", propertyViews(classroomId, false, studentOrNull(subject) != null));
        return result;
    }

    @Transactional
    public Map<String, Object> replaceLayout(String subject, UUID classroomId, int rows, int columns) {
        authorization.authorize(subject, classroomId, ClassroomPermission.MANAGE_PROPERTIES, null);
        requireModule(classroomId, "REAL_ESTATE");
        if (rows < 1 || rows > 26 || columns < 1 || columns > 26 || rows * columns > 200) {
            throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_LAYOUT", "자리표는 최대 26행, 26열, 200자리까지 만들 수 있습니다.");
        }
        Integer activeContracts = queryForObject("select count(*) from property_contracts where classroom_id=? and status in ('PENDING','ACTIVE','OVERDUE')", Integer.class, classroomId);
        if (activeContracts != null && activeContracts > 0) {
            throw error(HttpStatus.CONFLICT, "ACTIVE_PROPERTY_CONTRACT_EXISTS", "계약 진행 중인 자리가 있어 자리표를 다시 만들 수 없습니다.");
        }
        Timestamp now = Timestamp.from(clock.instant());
        update("update properties set is_active=false,is_public=false,status='INACTIVE',state_version=state_version+1,status_changed_at=?,updated_at=? where classroom_id=? and is_active=true", now, now, classroomId);
        int changed = update("update property_layouts set row_count=?,column_count=?,state_version=state_version+1,updated_at=? where classroom_id=?", rows, columns, now, classroomId);
        if (changed == 0) update("insert into property_layouts(classroom_id,row_count,column_count,state_version,updated_at,created_at) values(?,?,?,0,?,?)", classroomId, rows, columns, now, now);
        for (int row = 1; row <= rows; row++) for (int column = 1; column <= columns; column++) {
            String coordinate = coordinate(row, column);
            update("insert into properties(id,classroom_id,coordinate,display_name,row_index,column_index,property_tier,is_public,is_active,allows_rent,allows_jeonse,allows_sale,allows_subscription,sale_price,deposit_amount,monthly_rent,rent_payment_cycle_days,contract_term_days,owner_kind,status,status_changed_at,created_at,updated_at) values(?,?,?,?,?,?,'GENERAL',false,true,false,false,false,false,0,0,0,7,28,'TEACHER','TEACHER_OWNED',?,?,?)", uuids.generate(), classroomId, coordinate, coordinate, row, column, now, now, now);
        }
        return layout(subject, classroomId);
    }

    @Transactional
    public Map<String, Object> createProperty(String subject, UUID classroomId, PropertyInput input) {
        authorization.authorize(subject, classroomId, ClassroomPermission.MANAGE_PROPERTIES, null);
        requireModule(classroomId, "REAL_ESTATE");
        validateProperty(input);
        int row = input.rowIndex() == null ? nextRow(classroomId) : input.rowIndex();
        int column = input.columnIndex() == null ? 1 : input.columnIndex();
        String coordinate = text(input.coordinate(), coordinate(row, column));
        Instant now = clock.instant(); UUID id = uuids.generate();
        String status = input.active() && input.isPublic() ? "AVAILABLE" : "TEACHER_OWNED";
        if (!input.active()) status = "INACTIVE";
        update("insert into properties(id,classroom_id,coordinate,display_name,row_index,column_index,property_tier,is_public,is_active,allows_rent,allows_jeonse,allows_sale,allows_subscription,sale_price,deposit_amount,monthly_rent,rent_payment_cycle_days,contract_term_days,owner_kind,status,status_changed_at,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, 'TEACHER', ?,?,?,?)", id, classroomId, coordinate, text(input.displayName(), coordinate), row, column, tier(input.tier()), input.isPublic(), input.active(), input.allowsRent(), input.allowsJeonse(), input.allowsSale(), input.allowsSubscription(), input.salePrice(), input.depositAmount(), input.monthlyRent(), input.paymentCycleDays(), input.contractTermDays(), status, now, now, now);
        return propertyView(property(classroomId, id, false));
    }

    @Transactional
    public Map<String, Object> updateProperty(String subject, UUID propertyId, PropertyInput input) {
        Property current = propertyById(propertyId, true);
        authorization.authorize(subject, current.classroomId(), ClassroomPermission.MANAGE_PROPERTIES, null);
        validateProperty(input);
        if (!input.active()) {
            Integer activeContracts = queryForObject("select count(*) from property_contracts where property_id=? and status in ('PENDING','ACTIVE','OVERDUE')", Integer.class, propertyId);
            if (activeContracts != null && activeContracts > 0) throw error(HttpStatus.CONFLICT, "ACTIVE_PROPERTY_CONTRACT_EXISTS", "계약이 끝난 자리만 비활성화할 수 있습니다.");
        }
        if (current.ownerKind().equals("STUDENT") && input.active() && !input.isPublic()) {
            throw error(HttpStatus.CONFLICT, "STUDENT_PROPERTY_VISIBILITY_REQUIRED", "학생 소유 자리는 임대 매물 관리로 공개 상태를 변경해 주세요.");
        }
        Instant now = clock.instant();
        String status = !input.active() ? "INACTIVE" : current.status();
        if (current.ownerKind().equals("TEACHER") && (current.status().equals("TEACHER_OWNED") || current.status().equals("AVAILABLE"))) status = input.isPublic() ? "AVAILABLE" : "TEACHER_OWNED";
        update("update properties set display_name=?,property_tier=?,is_public=?,is_active=?,allows_rent=?,allows_jeonse=?,allows_sale=?,allows_subscription=?,sale_price=?,deposit_amount=?,monthly_rent=?,rent_payment_cycle_days=?,contract_term_days=?,status=?,state_version=state_version+1,status_changed_at=?,updated_at=? where id=?", text(input.displayName(), current.displayName()), tier(input.tier()), input.isPublic(), input.active(), input.allowsRent(), input.allowsJeonse(), input.allowsSale(), input.allowsSubscription(), input.salePrice(), input.depositAmount(), input.monthlyRent(), input.paymentCycleDays(), input.contractTermDays(), status, now, now, propertyId);
        return propertyView(property(current.classroomId(), propertyId, false));
    }

    @Transactional
    public void deactivateProperty(String subject, UUID propertyId) {
        Property property = propertyById(propertyId, true);
        authorization.authorize(subject, property.classroomId(), ClassroomPermission.MANAGE_PROPERTIES, null);
        Integer active = queryForObject("select count(*) from property_contracts where property_id=? and status in ('PENDING','ACTIVE','OVERDUE')", Integer.class, propertyId);
        if (active != null && active > 0) throw error(HttpStatus.CONFLICT, "ACTIVE_PROPERTY_CONTRACT_EXISTS", "계약이 끝난 자리만 삭제하거나 비활성화할 수 있습니다.");
        Instant now = clock.instant();
        update("update properties set is_active=false,is_public=false,status='INACTIVE',state_version=state_version+1,status_changed_at=?,updated_at=? where id=?", now, now, propertyId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> available(String subject, UUID classroomId) {
        authorizeRead(subject, classroomId); requireModule(classroomId, "REAL_ESTATE");
        return propertyViews(classroomId, true, true);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> mine(String subject, UUID classroomId) {
        UUID studentId = student(subject); authorization.authorize(subject, classroomId, ClassroomPermission.USE_PROPERTIES, studentId);
        requireModule(classroomId, "REAL_ESTATE");
        return query("select distinct p.id,p.classroom_id,p.coordinate,p.display_name,p.property_tier,p.is_public,p.is_active,p.allows_rent,p.allows_jeonse,p.allows_sale,p.allows_subscription,p.sale_price,p.deposit_amount,p.monthly_rent,p.rent_payment_cycle_days,p.contract_term_days,p.owner_kind,p.owner_student_id,p.status,p.subscription_offer_id,p.subscription_winner_student_id from properties p left join property_contracts c on c.property_id=p.id and c.occupant_student_id=? and c.status in ('ACTIVE','OVERDUE') where p.classroom_id=? and (p.owner_student_id=? or c.id is not null) order by p.row_index,p.column_index", (rs, n) -> propertyView(property(rs), false), studentId, classroomId, studentId);
    }

    @Transactional
    public Map<String, Object> requestContract(String subject, UUID propertyId, ContractInput input) {
        UUID studentId = student(subject); Property property = propertyById(propertyId, true);
        authorization.authorize(subject, property.classroomId(), ClassroomPermission.USE_PROPERTIES, studentId);
        requireModule(property.classroomId(), "REAL_ESTATE");
        String type = requiredType(input.contractType(), "RENT", "JEONSE", "SALE");
        if (!property.active() || !property.isPublic() || !(property.status().equals("AVAILABLE") || property.status().equals("LISTED_FOR_RENT") || property.status().equals("LISTED_FOR_SALE") || property.status().equals("SUBSCRIPTION_OPEN"))) throw error(HttpStatus.CONFLICT, "PROPERTY_NOT_AVAILABLE", "현재 계약 가능한 자리가 아닙니다.");
        if (studentId.equals(property.ownerStudentId())) throw error(HttpStatus.CONFLICT, "PROPERTY_OWNER_CANNOT_CONTRACT", "본인 소유 자리는 계약할 수 없습니다.");
        Listing listing = property.status().equals("LISTED_FOR_RENT") ? activeListing(property.classroomId(), propertyId) : null;
        SaleListing saleListing = property.status().equals("LISTED_FOR_SALE") ? activeSaleListing(property.classroomId(), propertyId, true) : null;
        if (property.status().equals("LISTED_FOR_SALE") && !type.equals("SALE")) throw error(HttpStatus.CONFLICT, "SALE_LISTING_ONLY", "재판매 자리는 매매로만 신청할 수 있습니다.");
        if (property.status().equals("LISTED_FOR_SALE") && saleListing == null) throw error(HttpStatus.CONFLICT, "SALE_LISTING_NOT_FOUND", "판매 매물 정보를 찾을 수 없습니다.");
        if (property.status().equals("LISTED_FOR_RENT") && type.equals("SALE")) throw error(HttpStatus.CONFLICT, "LEASE_LISTING_ONLY", "임대 매물은 월세 또는 전세로만 신청할 수 있습니다.");
        if (type.equals("RENT") && !(listing == null ? property.rent() : listing.leaseType().equals("RENT"))) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "RENT_NOT_ALLOWED", "월세 계약이 허용되지 않은 자리입니다.");
        if (type.equals("JEONSE") && !(listing == null ? property.jeonse() : listing.leaseType().equals("JEONSE"))) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "JEONSE_NOT_ALLOWED", "전세 계약이 허용되지 않은 자리입니다.");
        if (type.equals("SALE") && !property.sale()) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "SALE_NOT_ALLOWED", "매매가 허용되지 않은 자리입니다.");
        if (type.equals("SALE")) requireNoOwnedProperty(property.classroomId(), studentId);
        if (type.equals("SALE") && property.status().equals("SUBSCRIPTION_OPEN")) requireWinner(property, studentId, input.subscriptionApplicationId());
        if (!type.equals("SALE") && property.status().equals("SUBSCRIPTION_OPEN")) throw error(HttpStatus.CONFLICT, "SUBSCRIPTION_SALE_ONLY", "청약 자리는 당첨자 매매만 가능합니다.");
        Integer active = queryForObject("select count(*) from property_contracts where property_id=? and status in ('PENDING','ACTIVE','OVERDUE')", Integer.class, propertyId);
        if (active != null && active > 0) throw error(HttpStatus.CONFLICT, "ACTIVE_PROPERTY_CONTRACT_EXISTS", "이미 진행 중인 계약이 있습니다.");
        long deposit = listing == null ? property.depositAmount() : listing.depositAmount();
        long rent = listing == null ? property.monthlyRent() : listing.monthlyRent();
        int cycle = listing == null ? property.paymentCycleDays() : listing.paymentCycleDays();
        int term = listing == null ? property.contractTermDays() : listing.contractTermDays();
        long salePrice = property.status().equals("SUBSCRIPTION_OPEN") ? offerPrice(property.subscriptionOfferId()) : saleListing == null ? property.salePrice() : saleListing.salePrice();
        if (type.equals("SALE") && salePrice < 1) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SALE_PRICE", "매매 가격을 먼저 설정해 주세요.");
        if (type.equals("RENT") && rent < 1) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_RENT_PRICE", "월세 금액을 먼저 설정해 주세요.");
        if (type.equals("JEONSE") && deposit < 1) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_JEONSE_DEPOSIT", "전세 보증금을 먼저 설정해 주세요.");
        Instant now = clock.instant(); UUID id = uuids.generate();
        update("insert into property_contracts(id,classroom_id,property_id,lease_listing_id,subscription_offer_id,subscription_application_id,contract_type,occupant_student_id,landlord_kind,landlord_student_id,deposit_amount,monthly_rent,sale_price,payment_cycle_days,contract_term_days,status,status_changed_at,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'PENDING',?,?,?)", id, property.classroomId(), propertyId, listing == null ? null : listing.id(), property.subscriptionOfferId(), input.subscriptionApplicationId(), type, studentId, property.ownerKind(), property.ownerStudentId(), type.equals("SALE") ? 0 : deposit, type.equals("RENT") ? rent : 0, type.equals("SALE") ? salePrice : 0, type.equals("RENT") ? cycle : null, type.equals("SALE") ? null : term, now, now, now);
        return contractView(contract(property.classroomId(), id, false));
    }

    @Transactional
    public Map<String, Object> decideContract(String subject, UUID contractId, boolean approve, String reason, String idempotencyKey) {
        Contract contract = contractById(contractId, true);
        authorization.authorize(subject, contract.classroomId(), ClassroomPermission.MANAGE_PROPERTIES, null);
        if (!contract.status().equals("PENDING")) throw error(HttpStatus.CONFLICT, "INVALID_CONTRACT_STATE", "승인 대기 중인 계약만 처리할 수 있습니다.");
        UUID teacherId = teacher(subject); Instant now = clock.instant();
        if (!approve) {
            update("update property_contracts set status='REJECTED',decision_reason=?,approved_by_user_id=?,approved_at=?,state_version=state_version+1,status_changed_at=?,updated_at=? where id=?", reason, teacherId, now, now, now, contractId);
            return contractView(contract(contract.classroomId(), contractId, false));
        }
        Property property = property(contract.classroomId(), contract.propertyId(), true);
        if (!property.active()) throw error(HttpStatus.CONFLICT, "PROPERTY_NOT_AVAILABLE", "비활성화된 자리는 승인할 수 없습니다.");
        if (contract.contractType().equals("SALE")) {
            if (!contract.landlordKind().equals(property.ownerKind()) || (contract.landlordStudentId() != null && !contract.landlordStudentId().equals(property.ownerStudentId()))) {
                throw error(HttpStatus.CONFLICT, "PROPERTY_OWNER_CHANGED", "신청 후 자리 소유자가 바뀌어 계약을 승인할 수 없습니다.");
            }
            Runnable completeSale = () -> {
                update("update property_contracts set status='COMPLETED',approved_by_user_id=?,decision_reason=?,approved_at=?,started_at=?,state_version=state_version+1,status_changed_at=?,updated_at=? where id=?", teacherId, reason, now, now, now, now, contract.id());
                update("update properties set owner_kind='STUDENT',owner_student_id=?,is_public=false,status='STUDENT_OWNED',subscription_winner_student_id=null,state_version=state_version+1,status_changed_at=?,updated_at=? where id=?", contract.occupantStudentId(), now, now, property.id());
                update("update property_sale_listings set status='PURCHASED',state_version=state_version+1,updated_at=? where property_id=? and status='ACTIVE'", now, property.id());
                if (contract.subscriptionApplicationId() != null) update("update subscription_applications set status='PURCHASED',updated_at=? where id=?", now, contract.subscriptionApplicationId());
            };
            if (contract.subscriptionApplicationId() == null) settle(contract, contract.salePrice(), "PROPERTY_SALE", teacherId, idempotencyKey, now, completeSale);
            else settleSubscriptionSale(contract, teacherId, idempotencyKey, now, completeSale);
        } else {
            settle(contract, contract.depositAmount(), contract.contractType().equals("RENT") ? "PROPERTY_RENT_DEPOSIT" : "PROPERTY_JEONSE_DEPOSIT", teacherId, idempotencyKey, now, () -> {
                Instant end = now.plus(contract.contractTermDays(), ChronoUnit.DAYS);
                Instant next = contract.contractType().equals("RENT") ? now.plus(contract.paymentCycleDays(), ChronoUnit.DAYS) : null;
                update("update property_contracts set status='ACTIVE',approved_by_user_id=?,decision_reason=?,approved_at=?,started_at=?,ends_at=?,next_payment_at=?,state_version=state_version+1,status_changed_at=?,updated_at=? where id=?", teacherId, reason, now, now, end, next, now, now, contract.id());
                update("update properties set status='RENTED',state_version=state_version+1,status_changed_at=?,updated_at=? where id=?", now, now, property.id());
                if (next != null) update("insert into property_contract_payments(id,classroom_id,property_contract_id,due_at,amount,status,created_at,updated_at) values(?,?,?,?,?,'SCHEDULED',?,?)", uuids.generate(), contract.classroomId(), contract.id(), next, contract.monthlyRent(), now, now);
            });
        }
        return contractView(contract(contract.classroomId(), contractId, false));
    }

    @Transactional
    public Map<String, Object> terminate(String subject, UUID contractId, String idempotencyKey) {
        Contract contract = contractById(contractId, true);
        UUID studentId = studentOrNull(subject);
        if (studentId == null) authorization.authorize(subject, contract.classroomId(), ClassroomPermission.MANAGE_PROPERTIES, null);
        else {
            authorization.authorize(subject, contract.classroomId(), ClassroomPermission.USE_PROPERTIES, studentId);
            if (!studentId.equals(contract.occupantStudentId())) throw error(HttpStatus.FORBIDDEN, "RESOURCE_NOT_OWNED", "본인 계약만 종료할 수 있습니다.");
        }
        if (!(contract.status().equals("ACTIVE") || contract.status().equals("OVERDUE"))) throw error(HttpStatus.CONFLICT, "INVALID_CONTRACT_STATE", "진행 중인 임대 계약만 종료할 수 있습니다.");
        Instant now = clock.instant(); UUID actorId = studentId == null ? teacher(subject) : studentId;
        settleReverse(contract, contract.depositAmount(), actorId, idempotencyKey, now, () -> {
            update("update property_contracts set status='TERMINATED',terminated_at=?,state_version=state_version+1,status_changed_at=?,updated_at=? where id=?", now, now, now, contract.id());
            String next = contract.landlordKind().equals("STUDENT") && activeListing(contract.classroomId(), contract.propertyId()) != null ? "LISTED_FOR_RENT" : contract.landlordKind().equals("STUDENT") ? "STUDENT_OWNED" : "AVAILABLE";
            update("update properties set status=?,state_version=state_version+1,status_changed_at=?,updated_at=? where id=?", next, now, now, contract.propertyId());
        });
        return contractView(contract(contract.classroomId(), contractId, false));
    }

    @Transactional
    public Map<String, Object> createListing(String subject, UUID propertyId, ListingInput input) {
        UUID studentId = student(subject); Property property = propertyById(propertyId, true);
        authorization.authorize(subject, property.classroomId(), ClassroomPermission.USE_PROPERTIES, studentId);
        if (!property.ownerKind().equals("STUDENT") || !studentId.equals(property.ownerStudentId())) throw error(HttpStatus.FORBIDDEN, "PROPERTY_OWNER_REQUIRED", "본인 소유 자리만 임대 매물로 등록할 수 있습니다.");
        if (!property.active()) throw error(HttpStatus.CONFLICT, "PROPERTY_NOT_AVAILABLE", "비활성화된 자리는 임대할 수 없습니다.");
        validateListing(input); Instant now = clock.instant(); UUID id = uuids.generate();
        update("insert into property_lease_listings(id,classroom_id,property_id,landlord_student_id,lease_type,deposit_amount,monthly_rent,payment_cycle_days,contract_term_days,is_public,status,state_version,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?, 'ACTIVE',0,?,?)", id, property.classroomId(), propertyId, studentId, requiredType(input.leaseType(), "RENT", "JEONSE"), input.depositAmount(), input.monthlyRent(), input.paymentCycleDays(), input.contractTermDays(), input.isPublic(), now, now);
        update("update properties set status='LISTED_FOR_RENT',is_public=?,state_version=state_version+1,status_changed_at=?,updated_at=? where id=?", input.isPublic(), now, now, propertyId);
        return listingView(listing(property.classroomId(), id, false));
    }

    @Transactional
    public Map<String, Object> updateListing(String subject, UUID listingId, ListingInput input) {
        Listing listing = listingById(listingId, true); UUID studentId = student(subject);
        authorization.authorize(subject, listing.classroomId(), ClassroomPermission.USE_PROPERTIES, studentId);
        if (!studentId.equals(listing.landlordStudentId())) throw error(HttpStatus.FORBIDDEN, "PROPERTY_OWNER_REQUIRED", "본인 임대 매물만 수정할 수 있습니다.");
        validateListing(input); Instant now = clock.instant();
        update("update property_lease_listings set lease_type=?,deposit_amount=?,monthly_rent=?,payment_cycle_days=?,contract_term_days=?,is_public=?,state_version=state_version+1,updated_at=? where id=?", requiredType(input.leaseType(), "RENT", "JEONSE"), input.depositAmount(), input.monthlyRent(), input.paymentCycleDays(), input.contractTermDays(), input.isPublic(), now, listingId);
        return listingView(listing(listing.classroomId(), listingId, false));
    }

    @Transactional
    public void deleteListing(String subject, UUID listingId) {
        Listing listing = listingById(listingId, true); UUID studentId = student(subject);
        authorization.authorize(subject, listing.classroomId(), ClassroomPermission.USE_PROPERTIES, studentId);
        if (!studentId.equals(listing.landlordStudentId())) throw error(HttpStatus.FORBIDDEN, "PROPERTY_OWNER_REQUIRED", "본인 임대 매물만 삭제할 수 있습니다.");
        Integer active = queryForObject("select count(*) from property_contracts where lease_listing_id=? and status in ('PENDING','ACTIVE','OVERDUE')", Integer.class, listingId);
        if (active != null && active > 0) throw error(HttpStatus.CONFLICT, "ACTIVE_PROPERTY_CONTRACT_EXISTS", "계약 중인 매물은 삭제할 수 없습니다.");
        Instant now = clock.instant(); update("update property_lease_listings set status='CLOSED',state_version=state_version+1,updated_at=? where id=?", now, listingId);
        update("update properties set status='STUDENT_OWNED',is_public=false,state_version=state_version+1,status_changed_at=?,updated_at=? where id=?", now, now, listing.propertyId());
    }

    @Transactional
    public Map<String, Object> createSaleListing(String subject, UUID propertyId, long salePrice) {
        UUID studentId = student(subject); Property property = propertyById(propertyId, true);
        authorization.authorize(subject, property.classroomId(), ClassroomPermission.USE_PROPERTIES, studentId);
        requireModule(property.classroomId(), "REAL_ESTATE");
        if (!property.active() || !property.ownerKind().equals("STUDENT") || !studentId.equals(property.ownerStudentId())) throw error(HttpStatus.FORBIDDEN, "PROPERTY_OWNER_REQUIRED", "본인 소유 자리만 판매할 수 있습니다.");
        if (!property.status().equals("STUDENT_OWNED")) throw error(HttpStatus.CONFLICT, "PROPERTY_NOT_AVAILABLE", "계약이나 판매가 진행 중인 자리는 다시 판매할 수 없습니다.");
        if (!property.sale() || salePrice < 1) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SALE_PRICE", "판매 가능한 자리와 1 이상의 가격을 확인해 주세요.");
        Integer active = queryForObject("select count(*) from property_contracts where property_id=? and status in ('PENDING','ACTIVE','OVERDUE')", Integer.class, propertyId);
        if (active != null && active > 0) throw error(HttpStatus.CONFLICT, "ACTIVE_PROPERTY_CONTRACT_EXISTS", "진행 중인 계약이 있어 판매할 수 없습니다.");
        Instant now = clock.instant(); UUID id = uuids.generate();
        update("insert into property_sale_listings(id,classroom_id,property_id,seller_student_id,sale_price,status,state_version,created_at,updated_at) values(?,?,?,?,?,'ACTIVE',0,?,?)", id, property.classroomId(), propertyId, studentId, salePrice, now, now);
        update("update properties set sale_price=?,is_public=true,status='LISTED_FOR_SALE',state_version=state_version+1,status_changed_at=?,updated_at=? where id=?", salePrice, now, now, propertyId);
        return saleListingView(activeSaleListing(property.classroomId(), propertyId, false));
    }

    @Transactional
    public void cancelSaleListing(String subject, UUID propertyId) {
        UUID studentId = student(subject); Property property = propertyById(propertyId, true);
        authorization.authorize(subject, property.classroomId(), ClassroomPermission.USE_PROPERTIES, studentId);
        requireModule(property.classroomId(), "REAL_ESTATE");
        SaleListing listing = activeSaleListing(property.classroomId(), propertyId, true);
        if (listing == null || !studentId.equals(listing.sellerStudentId())) throw error(HttpStatus.FORBIDDEN, "PROPERTY_OWNER_REQUIRED", "본인 판매 매물만 취소할 수 있습니다.");
        Integer pending = queryForObject("select count(*) from property_contracts where property_id=? and status='PENDING'", Integer.class, propertyId);
        if (pending != null && pending > 0) throw error(HttpStatus.CONFLICT, "PENDING_PROPERTY_CONTRACT_EXISTS", "구매 승인 대기 건을 먼저 처리해 주세요.");
        Instant now = clock.instant();
        update("update property_sale_listings set status='CLOSED',state_version=state_version+1,updated_at=? where id=?", now, listing.id());
        update("update properties set is_public=false,status='STUDENT_OWNED',state_version=state_version+1,status_changed_at=?,updated_at=? where id=?", now, now, propertyId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> contractsForClassroom(String subject, UUID classroomId) {
        authorization.authorize(subject, classroomId, ClassroomPermission.MANAGE_PROPERTIES, null);
        requireModule(classroomId, "REAL_ESTATE");
        return query("select c.id,c.property_id,c.contract_type,c.occupant_student_id,c.landlord_kind,c.landlord_student_id,c.deposit_amount,c.monthly_rent,c.sale_price,c.status,p.coordinate,p.display_name,s.nickname,s.roster_number from property_contracts c join properties p on p.id=c.property_id join classroom_students s on s.id=c.occupant_student_id where c.classroom_id=? and c.status='PENDING' order by c.created_at", (rs,n) -> { Map<String,Object> item=new LinkedHashMap<>(); item.put("contract_id",rs.getObject(1,UUID.class)); item.put("property_id",rs.getObject(2,UUID.class)); item.put("contract_type",rs.getString(3)); item.put("occupant_student_id",rs.getObject(4,UUID.class)); item.put("landlord_kind",rs.getString(5)); item.put("landlord_student_id",rs.getObject(6,UUID.class)); item.put("deposit_amount",rs.getLong(7)); item.put("monthly_rent",rs.getLong(8)); item.put("sale_price",rs.getLong(9)); item.put("status",rs.getString(10)); item.put("coordinate",rs.getString(11)); item.put("display_name",rs.getString(12)); item.put("student_nickname",rs.getString(13)); item.put("roster_number",rs.getInt(14)); return item; }, classroomId);
    }

    @Transactional
    public Map<String, Object> createOffer(String subject, UUID classroomId, OfferInput input) {
        authorization.authorize(subject, classroomId, ClassroomPermission.MANAGE_SUBSCRIPTION_OFFERS, null);
        requireModule(classroomId, "HOUSING_SUBSCRIPTION"); validateOffer(input);
        if (input.propertyIds() == null || input.propertyIds().isEmpty() || input.supplyQuantity() > input.propertyIds().size()) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SUBSCRIPTION_OFFER", "공급 수만큼 청약 대상 자리를 선택해 주세요.");
        Instant now = clock.instant(); UUID id = uuids.generate();
        update("insert into subscription_offers(id,classroom_id,title,application_opens_at,application_closes_at,sale_price,supply_quantity,minimum_deposit_count,minimum_balance,draw_method,status,state_version,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,'RANDOM','OPEN',0,?,?)", id, classroomId, text(input.title(), "청약"), input.opensAt(), input.closesAt(), input.salePrice(), input.supplyQuantity(), input.minimumDepositCount(), input.minimumBalance(), now, now);
        for (UUID propertyId : input.propertyIds()) {
            Property property = property(classroomId, propertyId, true);
            if (!property.active() || !property.ownerKind().equals("TEACHER") || !(property.status().equals("AVAILABLE") || property.status().equals("TEACHER_OWNED"))) throw error(HttpStatus.CONFLICT, "PROPERTY_NOT_AVAILABLE", "교사 보유의 계약 가능 자리만 청약으로 지정할 수 있습니다.");
            update("insert into subscription_offer_properties(classroom_id,subscription_offer_id,property_id,created_at) values(?,?,?,?)", classroomId, id, propertyId, now);
            update("update properties set is_public=true,allows_subscription=true,subscription_offer_id=?,status='SUBSCRIPTION_OPEN',state_version=state_version+1,status_changed_at=?,updated_at=? where id=?", id, now, now, propertyId);
        }
        return offerView(offer(classroomId, id, false));
    }

    @Transactional
    public Map<String, Object> applyOffer(String subject, UUID offerId) {
        Offer offer = offerById(offerId, true); UUID studentId = student(subject);
        authorization.authorize(subject, offer.classroomId(), ClassroomPermission.USE_PROPERTIES, studentId); requireModule(offer.classroomId(), "HOUSING_SUBSCRIPTION");
        Instant now = clock.instant();
        if (!offer.status().equals("OPEN") || now.isBefore(offer.opensAt()) || now.isAfter(offer.closesAt())) throw error(HttpStatus.CONFLICT, "SUBSCRIPTION_NOT_OPEN", "현재 청약 신청 기간이 아닙니다.");
        SubscriptionAccount account = subscriptionAccount(offer.classroomId(), studentId, false);
        requireNoOwnedProperty(offer.classroomId(), studentId);
        long requiredBalance = Math.max(offer.minimumBalance(), offer.salePrice());
        if (account == null || account.depositCount() < offer.minimumDepositCount() || account.totalDepositAmount() < requiredBalance) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "SUBSCRIPTION_REQUIREMENT_NOT_MET", "청약통장 납입 횟수 또는 분양가 이상의 예치금 조건을 충족하지 못했습니다.");
        Integer activeApplications = queryForObject("select count(*) from subscription_applications where classroom_id=? and classroom_student_id=? and status in ('APPLIED','WON')", Integer.class, offer.classroomId(), studentId);
        if (activeApplications != null && activeApplications > 0) throw error(HttpStatus.CONFLICT, "ACTIVE_SUBSCRIPTION_APPLICATION_EXISTS", "진행 중인 청약은 한 번에 하나만 신청할 수 있습니다.");
        UUID id = uuids.generate();
        try { update("insert into subscription_applications(id,classroom_id,subscription_offer_id,classroom_student_id,deposit_count_snapshot,deposit_amount_snapshot,status,applied_at,created_at,updated_at) values(?,?,?,?,?,?,'APPLIED',?,?,?)", id, offer.classroomId(), offerId, studentId, account.depositCount(), account.totalDepositAmount(), now, now, now); }
        catch (RuntimeException exception) { throw error(HttpStatus.CONFLICT, "SUBSCRIPTION_ALREADY_APPLIED", "이미 이 청약에 신청했습니다."); }
        return applicationView(application(offer.classroomId(), id));
    }

    @Transactional
    public List<Map<String, Object>> drawOffer(String subject, UUID offerId) {
        Offer offer = offerById(offerId, true); authorization.authorize(subject, offer.classroomId(), ClassroomPermission.MANAGE_SUBSCRIPTION_OFFERS, null);
        Instant now = clock.instant(); if (!offer.status().equals("OPEN") || now.isBefore(offer.closesAt())) throw error(HttpStatus.CONFLICT, "SUBSCRIPTION_DRAW_NOT_READY", "모집 종료 후 추첨할 수 있습니다.");
        UUID teacherId = teacher(subject); List<Application> applicants = query("select id,classroom_id,subscription_offer_id,classroom_student_id,deposit_count_snapshot,deposit_amount_snapshot,assigned_property_id,status,applied_at from subscription_applications where subscription_offer_id=? and status='APPLIED'", (rs,n)->application(rs), offerId);
        List<UUID> seats = query("select property_id from subscription_offer_properties where subscription_offer_id=? order by property_id", (rs,n)->rs.getObject(1,UUID.class), offerId);
        Collections.shuffle(applicants); int winners = Math.min(Math.min(offer.supplyQuantity(), seats.size()), applicants.size());
        for (int index=0; index<applicants.size(); index++) { Application app=applicants.get(index); if(index<winners) { UUID propertyId=seats.get(index); update("update subscription_applications set status='WON',assigned_property_id=?,drawn_at=?,updated_at=? where id=?", propertyId, now, now, app.id()); update("update properties set subscription_winner_student_id=?,state_version=state_version+1,status_changed_at=?,updated_at=? where id=?", app.studentId(), now, now, propertyId); } else update("update subscription_applications set status='LOST',drawn_at=?,updated_at=? where id=?",now,now,app.id()); }
        for (int index = winners; index < seats.size(); index++) update("update properties set subscription_offer_id=null,subscription_winner_student_id=null,allows_subscription=false,status='AVAILABLE',state_version=state_version+1,status_changed_at=?,updated_at=? where id=?", now, now, seats.get(index));
        update("update subscription_offers set status='DRAWN',drawn_at=?,drawn_by_user_id=?,state_version=state_version+1,updated_at=? where id=?",now,teacherId,now,offerId);
        return query("select id,classroom_id,subscription_offer_id,classroom_student_id,deposit_count_snapshot,deposit_amount_snapshot,assigned_property_id,status,applied_at from subscription_applications where subscription_offer_id=? order by created_at",(rs,n)->applicationView(application(rs)),offerId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> offers(String subject, UUID classroomId) {
        authorizeRead(subject, classroomId); requireModule(classroomId, "HOUSING_SUBSCRIPTION");
        return query("select o.id,o.classroom_id,o.title,o.application_opens_at,o.application_closes_at,o.sale_price,o.supply_quantity,o.minimum_deposit_count,o.minimum_balance,o.status,(select count(*) from subscription_applications a where a.subscription_offer_id=o.id and a.status in ('APPLIED','WON')) from subscription_offers o where o.classroom_id=? order by o.created_at desc", (rs,n) -> { Offer offer=new Offer(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getTimestamp(4).toInstant(),rs.getTimestamp(5).toInstant(),rs.getLong(6),rs.getInt(7),rs.getInt(8),rs.getLong(9),rs.getString(10)); Map<String,Object> item=new LinkedHashMap<>(offerView(offer)); item.put("applicant_count",rs.getInt(11)); item.put("property_ids",query("select property_id from subscription_offer_properties where subscription_offer_id=? order by property_id",(seatRs,index)->seatRs.getObject(1,UUID.class),offer.id())); return item; }, classroomId);
    }

    @Transactional
    public void cancelOffer(String subject, UUID offerId) {
        Offer offer=offerById(offerId,true); authorization.authorize(subject,offer.classroomId(),ClassroomPermission.MANAGE_SUBSCRIPTION_OFFERS,null); requireModule(offer.classroomId(),"HOUSING_SUBSCRIPTION");
        if (!offer.status().equals("OPEN")) throw error(HttpStatus.CONFLICT,"INVALID_SUBSCRIPTION_STATE","진행 중인 청약만 취소할 수 있습니다.");
        Instant now=clock.instant(); update("update subscription_offers set status='CANCELED',state_version=state_version+1,updated_at=? where id=?",now,offerId); update("update subscription_applications set status='CANCELED',updated_at=? where subscription_offer_id=? and status='APPLIED'",now,offerId); update("update properties set subscription_offer_id=null,subscription_winner_student_id=null,allows_subscription=false,status='AVAILABLE',state_version=state_version+1,status_changed_at=?,updated_at=? where subscription_offer_id=?",now,now,offerId);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> applications(String subject, UUID classroomId) {
        UUID studentId=student(subject); authorization.authorize(subject,classroomId,ClassroomPermission.USE_PROPERTIES,studentId); requireModule(classroomId,"HOUSING_SUBSCRIPTION");
        return query("select id,classroom_id,subscription_offer_id,classroom_student_id,deposit_count_snapshot,deposit_amount_snapshot,assigned_property_id,status,applied_at from subscription_applications where classroom_id=? and classroom_student_id=? order by applied_at desc",(rs,n)->applicationView(application(rs)),classroomId,studentId);
    }

    @Transactional
    public void cancelApplication(String subject, UUID applicationId) {
        UUID studentId=student(subject); Application application=applicationById(applicationId,true); authorization.authorize(subject,application.classroomId(),ClassroomPermission.USE_PROPERTIES,studentId); requireModule(application.classroomId(),"HOUSING_SUBSCRIPTION");
        if (!studentId.equals(application.studentId())) throw error(HttpStatus.FORBIDDEN,"RESOURCE_NOT_OWNED","본인 청약만 취소할 수 있습니다.");
        if (!application.status().equals("APPLIED")) throw error(HttpStatus.CONFLICT,"INVALID_SUBSCRIPTION_STATE","추첨 전 신청만 취소할 수 있습니다.");
        update("update subscription_applications set status='CANCELED',updated_at=? where id=?",clock.instant(),applicationId);
    }

    @Transactional
    public Map<String, Object> openSubscriptionAccount(String subject, UUID classroomId) {
        UUID studentId=student(subject); authorization.authorize(subject,classroomId,ClassroomPermission.USE_PROPERTIES,studentId); requireModule(classroomId,"HOUSING_SUBSCRIPTION");
        AutoDepositPolicy policy=autoDepositPolicy(classroomId); Instant now=clock.instant(); Long autoAmount=policy.enabled()?policy.amount():null; Integer autoCycleDays=policy.enabled()?policy.cycleDays():null;
        try { update("insert into subscription_accounts(classroom_student_id,classroom_id,total_deposit_amount,deposit_count,last_deposited_at,auto_deposit_amount,auto_deposit_cycle_days,next_auto_deposit_at,status,state_version,created_at,updated_at) values(?,?,0,0,null,?,?,?,'ACTIVE',0,?,?)",studentId,classroomId,autoAmount,autoCycleDays,autoAmount==null?null:now.plus(autoCycleDays,ChronoUnit.DAYS),now,now); } catch(RuntimeException exception){ throw error(HttpStatus.CONFLICT,"SUBSCRIPTION_ACCOUNT_EXISTS","이미 청약통장이 개설되어 있습니다."); }
        return subscriptionView(subscriptionAccount(classroomId,studentId,false));
    }

    @Transactional
    public Map<String,Object> updateAutoDepositPolicy(String subject, UUID classroomId, boolean enabled, Long amount, Integer cycleDays) {
        authorization.authorize(subject,classroomId,ClassroomPermission.MANAGE_SUBSCRIPTION_OFFERS,null); requireModule(classroomId,"HOUSING_SUBSCRIPTION");
        if (enabled && (amount==null || amount<1 || cycleDays==null || cycleDays<1 || cycleDays>365)) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_SUBSCRIPTION_AUTO_DEPOSIT","자동 납입 금액과 주기를 함께 올바르게 입력해 주세요.");
        Instant now=clock.instant(); Long storedAmount=enabled?amount:null; Integer storedCycle=enabled?cycleDays:null;
        update("insert into subscription_auto_deposit_policies(classroom_id,enabled,amount,cycle_days,created_at,updated_at) values(?,?,?,?,?,?) on conflict(classroom_id) do update set enabled=excluded.enabled,amount=excluded.amount,cycle_days=excluded.cycle_days,state_version=subscription_auto_deposit_policies.state_version+1,updated_at=excluded.updated_at",classroomId,enabled,storedAmount,storedCycle,now,now);
        update("update subscription_accounts set auto_deposit_amount=?,auto_deposit_cycle_days=?,next_auto_deposit_at=?,state_version=state_version+1,updated_at=? where classroom_id=? and status='ACTIVE'",storedAmount,storedCycle,enabled?now.plus(storedCycle,ChronoUnit.DAYS):null,now,classroomId);
        return autoDepositPolicyView(autoDepositPolicy(classroomId));
    }

    @Transactional
    public Map<String, Object> depositSubscription(String subject, UUID classroomId, long amount, String idempotencyKey) {
        UUID studentId=student(subject); authorization.authorize(subject,classroomId,ClassroomPermission.USE_PROPERTIES,studentId); requireModule(classroomId,"HOUSING_SUBSCRIPTION");
        SubscriptionAccount account=subscriptionAccount(classroomId,studentId,true); if(account==null) throw error(HttpStatus.NOT_FOUND,"SUBSCRIPTION_ACCOUNT_NOT_FOUND","먼저 청약통장을 개설해 주세요."); if(amount<1) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_DEPOSIT_AMOUNT","납입 금액은 1 이상이어야 합니다.");
        collectSubscription(account,amount,"MANUAL",idempotencyKey,clock.instant()); return subscriptionView(subscriptionAccount(classroomId,studentId,false));
    }

    @Transactional
    public Map<String,Object> subscriptionAccountView(String subject, UUID classroomId) {
        UUID studentId=student(subject); authorization.authorize(subject,classroomId,ClassroomPermission.USE_PROPERTIES,studentId); requireModule(classroomId,"HOUSING_SUBSCRIPTION"); SubscriptionAccount account=subscriptionAccount(classroomId,studentId,false); if(account==null) throw error(HttpStatus.NOT_FOUND,"SUBSCRIPTION_ACCOUNT_NOT_FOUND","청약통장이 없습니다.");
        Map<String,Object> result=subscriptionView(account); result.put("deposits",query("select id,amount,deposited_at,deposit_type,transaction_group_id from subscription_account_deposits where classroom_student_id=? order by deposited_at desc",(rs,n)->Map.of("deposit_id",rs.getObject(1,UUID.class),"amount",rs.getLong(2),"deposited_at",rs.getTimestamp(3).toInstant(),"deposit_type",rs.getString(4),"transaction_group_id",rs.getObject(5,UUID.class)),studentId)); return result;
    }

    @Transactional
    public void collectDueRentAndSubscriptions() {
        Instant now=clock.instant();
        Timestamp nowTimestamp=Timestamp.from(now);
        for (DueRent due : query("select p.id,p.classroom_id,p.property_contract_id,c.occupant_student_id,c.landlord_kind,c.landlord_student_id,p.amount,c.payment_cycle_days,c.ends_at,p.due_at from property_contract_payments p join property_contracts c on c.id=p.property_contract_id where p.status in ('SCHEDULED','MISSED') and p.due_at<=? and c.status in ('ACTIVE','OVERDUE') for update of p skip locked",(rs,n)->new DueRent(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getString(5),rs.getObject(6,UUID.class),rs.getLong(7),rs.getInt(8),rs.getTimestamp(9).toInstant(),rs.getTimestamp(10).toInstant()),nowTimestamp)) settleRent(due,now);
        for (SubscriptionAccount account : query("select classroom_student_id,classroom_id,total_deposit_amount,deposit_count,last_deposited_at,auto_deposit_amount,auto_deposit_cycle_days,next_auto_deposit_at,status,state_version from subscription_accounts where status='ACTIVE' and next_auto_deposit_at<=? for update skip locked",(rs,n)->subscriptionAccount(rs),nowTimestamp)) { try { collectSubscription(account,account.autoAmount(),"AUTO","subscription-auto-"+account.studentId()+"-"+account.nextAutoAt().toEpochMilli(),now); } catch (RuntimeException ignored) { update("update subscription_accounts set next_auto_deposit_at=?,updated_at=? where classroom_student_id=?",Timestamp.from(now.plus(account.autoCycleDays(),ChronoUnit.DAYS)),nowTimestamp,account.studentId()); } }
    }

    private void settleRent(DueRent due, Instant now) {
        Contract contract=contract(due.classroomId(),due.contractId(),true); LedgerAccount wallet=wallet(due.classroomId(),due.tenantId(),now); if(wallet.getCurrentBalance()<due.amount()) { update("update property_contract_payments set status='MISSED',updated_at=? where id=?",now,due.paymentId()); update("update property_contracts set status='OVERDUE',state_version=state_version+1,status_changed_at=?,updated_at=? where id=? and status='ACTIVE'",now,now,due.contractId()); return; }
        settle(contract,due.amount(),"PROPERTY_RENT_PAYMENT",due.tenantId(),"property-rent-"+due.paymentId(),now,()->{ update("update property_contract_payments set status='PAID',paid_at=?,transaction_group_id=(select id from transaction_groups where classroom_id=? and request_id=? order by created_at desc limit 1),updated_at=? where id=?",now,due.classroomId(),"property-rent-"+due.paymentId(),now,due.paymentId()); Instant next=due.dueAt().plus(due.paymentCycleDays(),ChronoUnit.DAYS); boolean schedule=next.isBefore(due.endsAt()); update("update property_contracts set status='ACTIVE',next_payment_at=?,state_version=state_version+1,status_changed_at=?,updated_at=? where id=?",schedule?next:null,now,now,due.contractId()); if(schedule) update("insert into property_contract_payments(id,classroom_id,property_contract_id,due_at,amount,status,created_at,updated_at) values(?,?,?,?,?,'SCHEDULED',?,?)",uuids.generate(),due.classroomId(),due.contractId(),next,due.amount(),now,now); });
    }

    private void collectSubscription(SubscriptionAccount account,long amount,String type,String idempotencyKey,Instant now) {
        LedgerAccount wallet=wallet(account.classroomId(),account.studentId(),now); LedgerAccount liability=system(account.classroomId(),SUBSCRIPTION_LIABILITY,LedgerAccountType.SUBSCRIPTION_LIABILITY,now);
        transactions.execute(LedgerTransactionCommand.original(account.classroomId(),"SUBSCRIPTION_DEPOSIT",LedgerActorType.STUDENT,account.studentId(),null,"/students/me/subscription-account/deposits",idempotencyKey,idempotencyKey,uuids.generate().toString(),"Housing subscription account deposit",List.of(new LedgerTransactionPosting(wallet.getId(),-amount),new LedgerTransactionPosting(liability.getId(),amount)),now),List.of(new LedgerDomainMutation(){public void validate(){} public void apply(){Instant next=account.autoAmount()==null?null:now.plus(account.autoCycleDays(),ChronoUnit.DAYS);update("update subscription_accounts set total_deposit_amount=total_deposit_amount+?,deposit_count=deposit_count+1,last_deposited_at=?,next_auto_deposit_at=?,state_version=state_version+1,updated_at=? where classroom_student_id=?",amount,now,next,now,account.studentId());update("insert into subscription_account_deposits(id,classroom_id,classroom_student_id,amount,deposited_at,deposit_type,transaction_group_id,created_at) values(?,?,?,?,?,?,(select id from transaction_groups where classroom_id=? and request_id=? order by created_at desc limit 1),?)",uuids.generate(),account.classroomId(),account.studentId(),amount,now,type,account.classroomId(),idempotencyKey,now);}}));
    }

    private void settleSubscriptionSale(Contract contract,UUID actorId,String idempotencyKey,Instant now,Runnable mutation) {
        SubscriptionAccount account=subscriptionAccount(contract.classroomId(),contract.occupantStudentId(),true);
        if(account==null||account.totalDepositAmount()<contract.salePrice()) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"SUBSCRIPTION_BALANCE_INSUFFICIENT","청약통장 잔액이 분양가보다 부족합니다.");
        LedgerAccount liability=system(contract.classroomId(),SUBSCRIPTION_LIABILITY,LedgerAccountType.SUBSCRIPTION_LIABILITY,now);
        LedgerAccount recipient=landlordAccount(contract.classroomId(),contract.landlordKind(),contract.landlordStudentId(),now);
        transactions.execute(LedgerTransactionCommand.original(contract.classroomId(),"SUBSCRIPTION_PROPERTY_SALE",LedgerActorType.TEACHER,actorId,null,"/contracts/{contract_id}/approve",idempotencyKey,idempotencyKey,uuids.generate().toString(),"Housing subscription property purchase",List.of(new LedgerTransactionPosting(liability.getId(),-contract.salePrice()),new LedgerTransactionPosting(recipient.getId(),contract.salePrice())),now),List.of(new LedgerDomainMutation(){public void validate(){} public void apply(){update("update subscription_accounts set total_deposit_amount=total_deposit_amount-?,state_version=state_version+1,updated_at=? where classroom_student_id=?",contract.salePrice(),now,contract.occupantStudentId());mutation.run();}}));
    }

    private void settle(Contract contract,long amount,String transactionType,UUID actorId,String idempotencyKey,Instant now,Runnable mutation) { if(amount==0){mutation.run();return;} LedgerAccount payer=wallet(contract.classroomId(),contract.occupantStudentId(),now); LedgerAccount recipient=landlordAccount(contract.classroomId(),contract.landlordKind(),contract.landlordStudentId(),now); transactions.execute(LedgerTransactionCommand.original(contract.classroomId(),transactionType,actorId.equals(contract.occupantStudentId())?LedgerActorType.STUDENT:LedgerActorType.TEACHER,actorId,null,"/properties/contracts",idempotencyKey,idempotencyKey,uuids.generate().toString(),transactionType,List.of(new LedgerTransactionPosting(payer.getId(),-amount),new LedgerTransactionPosting(recipient.getId(),amount)),now),List.of(new LedgerDomainMutation(){public void validate(){} public void apply(){mutation.run();}})); }
    private void settleReverse(Contract contract,long amount,UUID actorId,String idempotencyKey,Instant now,Runnable mutation) { if(amount==0){mutation.run();return;} LedgerAccount payer=landlordAccount(contract.classroomId(),contract.landlordKind(),contract.landlordStudentId(),now); LedgerAccount recipient=wallet(contract.classroomId(),contract.occupantStudentId(),now); transactions.execute(LedgerTransactionCommand.original(contract.classroomId(),"PROPERTY_DEPOSIT_REFUND",actorId.equals(contract.occupantStudentId())?LedgerActorType.STUDENT:LedgerActorType.TEACHER,actorId,null,"/contracts/{contract_id}/terminate",idempotencyKey,idempotencyKey,uuids.generate().toString(),"Return property deposit",List.of(new LedgerTransactionPosting(payer.getId(),-amount),new LedgerTransactionPosting(recipient.getId(),amount)),now),List.of(new LedgerDomainMutation(){public void validate(){} public void apply(){mutation.run();}})); }

    private LedgerAccount wallet(UUID classroomId,UUID studentId,Instant now){return ledger.findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(classroomId,studentId,LedgerAccountType.STUDENT_WALLET,LedgerAccountStatus.ACTIVE).orElseGet(()->ledger.save(LedgerAccount.studentWallet(uuids.generate(),classroomId,studentId,now)));}
    private LedgerAccount system(UUID classroomId,String key,LedgerAccountType type,Instant now){return ledger.findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(classroomId,key,type,LedgerAccountStatus.ACTIVE).orElseGet(()->ledger.save(LedgerAccount.systemAccount(uuids.generate(),classroomId,key,type,now)));}
    private LedgerAccount landlordAccount(UUID classroomId,String kind,UUID studentId,Instant now){return kind.equals("STUDENT")?wallet(classroomId,studentId,now):system(classroomId,PROPERTY_REVENUE,LedgerAccountType.PROPERTY_REVENUE,now);}

    private void authorizeRead(String subject,UUID classroomId){UUID student=studentOrNull(subject); authorization.authorize(subject,classroomId,ClassroomPermission.READ_PROPERTIES,student);}
    private UUID student(String subject){UUID id=studentOrNull(subject);if(id==null)throw error(HttpStatus.FORBIDDEN,"ROLE_NOT_ALLOWED","학생 기능입니다.");return id;}
    private UUID studentOrNull(String subject){try{return users.findByExternalSubject(subject).isPresent()?null:UUID.fromString(subject);}catch(Exception exception){return null;}}
    private UUID teacher(String subject){return users.findByExternalSubject(subject).orElseThrow(()->error(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","교사 로그인이 필요합니다.")).getId();}
    private void requireModule(UUID classroomId,String code){if(!modules.existsByClassroomIdAndModuleCodeAndStatus(classroomId,code,"ACTIVE"))throw error(HttpStatus.CONFLICT,"MODULE_NOT_ACTIVE","부동산 모듈을 먼저 활성화해 주세요.");}
    private void requireNoOwnedProperty(UUID classroomId,UUID studentId){Integer owned=queryForObject("select (select count(*) from properties where classroom_id=? and owner_student_id=? and is_active=true and status in ('STUDENT_OWNED','LISTED_FOR_SALE','LISTED_FOR_RENT','RENTED'))+(select count(*) from property_contracts where classroom_id=? and occupant_student_id=? and contract_type='SALE' and status='PENDING')",Integer.class,classroomId,studentId,classroomId,studentId);if(owned!=null&&owned>0)throw error(HttpStatus.CONFLICT,"ALREADY_HAS_PROPERTY","한 학생은 한 자리만 소유하거나 구매 신청할 수 있습니다.");}
    private static ClassroomOperationException error(HttpStatus status,String code,String message){return new ClassroomOperationException(status,code,message);}
    private static String text(String value,String fallback){return value==null||value.isBlank()?fallback:value.trim();}
    private static String tier(String value){return "POPULAR".equalsIgnoreCase(value)?"POPULAR":"GENERAL";}
    private static String requiredType(String value,String... allowed){for(String candidate:allowed)if(candidate.equalsIgnoreCase(value))return candidate;throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PROPERTY_TYPE","허용되지 않은 계약 유형입니다.");}
    private static String coordinate(int row,int column){return String.valueOf((char)('A'+row-1))+column;}
    private int nextRow(UUID classroomId){Integer value=queryForObject("select coalesce(max(row_index),0)+1 from properties where classroom_id=?",Integer.class,classroomId);return value==null?1:value;}
    private void validateProperty(PropertyInput input){if(input==null||input.salePrice()<0||input.depositAmount()<0||input.monthlyRent()<0||input.paymentCycleDays()<1||input.paymentCycleDays()>365||input.contractTermDays()<1||input.contractTermDays()>365)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_PROPERTY","자리 가격과 계약 조건을 확인해 주세요.");}
    private void validateListing(ListingInput input){if(input==null||input.depositAmount()<0||input.monthlyRent()<0||input.paymentCycleDays()<1||input.paymentCycleDays()>365||input.contractTermDays()<1||input.contractTermDays()>365||("RENT".equalsIgnoreCase(input.leaseType())&&input.monthlyRent()<1)||("JEONSE".equalsIgnoreCase(input.leaseType())&&input.depositAmount()<1))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_LEASE_LISTING","임대 조건을 확인해 주세요.");}
    private void validateOffer(OfferInput input){if(input==null||input.opensAt()==null||input.closesAt()==null||!input.closesAt().isAfter(input.opensAt())||input.salePrice()<1||input.supplyQuantity()<1||input.minimumDepositCount()<0||input.minimumBalance()<0)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_SUBSCRIPTION_OFFER","청약 조건을 확인해 주세요.");}

    private Property propertyById(UUID id,boolean lock){Property value=query("select id,classroom_id,coordinate,display_name,property_tier,is_public,is_active,allows_rent,allows_jeonse,allows_sale,allows_subscription,sale_price,deposit_amount,monthly_rent,rent_payment_cycle_days,contract_term_days,owner_kind,owner_student_id,status,subscription_offer_id,subscription_winner_student_id from properties where id=?"+(lock?" for update":""),rs->rs.next()?property(rs):null,id);if(value==null)throw error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","자리를 찾을 수 없습니다.");return value;}
    private Property property(UUID classroomId,UUID id,boolean lock){Property value=query("select id,classroom_id,coordinate,display_name,property_tier,is_public,is_active,allows_rent,allows_jeonse,allows_sale,allows_subscription,sale_price,deposit_amount,monthly_rent,rent_payment_cycle_days,contract_term_days,owner_kind,owner_student_id,status,subscription_offer_id,subscription_winner_student_id from properties where classroom_id=? and id=?"+(lock?" for update":""),rs->rs.next()?property(rs):null,classroomId,id);if(value==null)throw error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","자리를 찾을 수 없습니다.");return value;}
    private static Property property(java.sql.ResultSet rs)throws java.sql.SQLException{return new Property(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getString(5),rs.getBoolean(6),rs.getBoolean(7),rs.getBoolean(8),rs.getBoolean(9),rs.getBoolean(10),rs.getBoolean(11),rs.getLong(12),rs.getLong(13),rs.getLong(14),rs.getInt(15),rs.getInt(16),rs.getString(17),rs.getObject(18,UUID.class),rs.getString(19),rs.getObject(20,UUID.class),rs.getObject(21,UUID.class));}
    private List<Map<String,Object>> propertyViews(UUID classroomId,boolean onlyAvailable,boolean studentProjection){String visibility=onlyAvailable?" and is_active=true and is_public=true and status in ('AVAILABLE','LISTED_FOR_RENT','LISTED_FOR_SALE','SUBSCRIPTION_OPEN')":studentProjection?" and is_active=true":"";String sql="select id,classroom_id,coordinate,display_name,property_tier,is_public,is_active,allows_rent,allows_jeonse,allows_sale,allows_subscription,sale_price,deposit_amount,monthly_rent,rent_payment_cycle_days,contract_term_days,owner_kind,owner_student_id,status,subscription_offer_id,subscription_winner_student_id from properties where classroom_id=?"+visibility+" order by row_index,column_index";return query(sql,(rs,n)->propertyView(property(rs),studentProjection),classroomId);}
    private static Map<String,Object> propertyView(Property p){return propertyView(p,false);}
    private static Map<String,Object> propertyView(Property p,boolean hideStudentOwner){Map<String,Object> m=new LinkedHashMap<>();m.put("property_id",p.id());m.put("classroom_id",p.classroomId());m.put("coordinate",p.coordinate());m.put("display_name",p.displayName());m.put("tier",p.tier());m.put("is_public",p.isPublic());m.put("is_active",p.active());m.put("allows_rent",p.rent());m.put("allows_jeonse",p.jeonse());m.put("allows_sale",p.sale());m.put("allows_subscription",p.subscription());m.put("sale_price",p.salePrice());m.put("deposit_amount",p.depositAmount());m.put("monthly_rent",p.monthlyRent());m.put("payment_cycle_days",p.paymentCycleDays());m.put("contract_term_days",p.contractTermDays());m.put("owner_kind",p.ownerKind());m.put("owner_student_id",hideStudentOwner?null:p.ownerStudentId());m.put("status",p.status());m.put("subscription_offer_id",p.subscriptionOfferId());return m;}
    private Listing activeListing(UUID classroomId,UUID propertyId){return query("select id,classroom_id,property_id,landlord_student_id,lease_type,deposit_amount,monthly_rent,payment_cycle_days,contract_term_days,is_public,status from property_lease_listings where classroom_id=? and property_id=? and status='ACTIVE' and is_public=true order by created_at desc limit 1",rs->rs.next()?listing(rs):null,classroomId,propertyId);}
    private Listing listingById(UUID id,boolean lock){Listing value=query("select id,classroom_id,property_id,landlord_student_id,lease_type,deposit_amount,monthly_rent,payment_cycle_days,contract_term_days,is_public,status from property_lease_listings where id=?"+(lock?" for update":""),rs->rs.next()?listing(rs):null,id);if(value==null)throw error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","임대 매물을 찾을 수 없습니다.");return value;}
    private Listing listing(UUID classroomId,UUID id,boolean lock){Listing value=query("select id,classroom_id,property_id,landlord_student_id,lease_type,deposit_amount,monthly_rent,payment_cycle_days,contract_term_days,is_public,status from property_lease_listings where classroom_id=? and id=?"+(lock?" for update":""),rs->rs.next()?listing(rs):null,classroomId,id);if(value==null)throw error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","임대 매물을 찾을 수 없습니다.");return value;}
    private static Listing listing(java.sql.ResultSet rs)throws java.sql.SQLException{return new Listing(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getString(5),rs.getLong(6),rs.getLong(7),rs.getInt(8),rs.getInt(9),rs.getBoolean(10),rs.getString(11));}
    private static Map<String,Object> listingView(Listing l){return Map.of("listing_id",l.id(),"property_id",l.propertyId(),"lease_type",l.leaseType(),"deposit_amount",l.depositAmount(),"monthly_rent",l.monthlyRent(),"payment_cycle_days",l.paymentCycleDays(),"contract_term_days",l.contractTermDays(),"is_public",l.isPublic(),"status",l.status());}
    private SaleListing activeSaleListing(UUID classroomId,UUID propertyId,boolean lock){return query("select id,classroom_id,property_id,seller_student_id,sale_price,status from property_sale_listings where classroom_id=? and property_id=? and status='ACTIVE'"+(lock?" for update":""),rs->rs.next()?saleListing(rs):null,classroomId,propertyId);}
    private static SaleListing saleListing(java.sql.ResultSet rs)throws java.sql.SQLException{return new SaleListing(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getLong(5),rs.getString(6));}
    private static Map<String,Object> saleListingView(SaleListing l){return Map.of("listing_id",l.id(),"property_id",l.propertyId(),"seller_student_id",l.sellerStudentId(),"sale_price",l.salePrice(),"status",l.status());}
    private Contract contractById(UUID id,boolean lock){Contract value=query("select id,classroom_id,property_id,lease_listing_id,subscription_offer_id,subscription_application_id,contract_type,occupant_student_id,landlord_kind,landlord_student_id,deposit_amount,monthly_rent,sale_price,payment_cycle_days,contract_term_days,started_at,ends_at,next_payment_at,status from property_contracts where id=?"+(lock?" for update":""),rs->rs.next()?contract(rs):null,id);if(value==null)throw error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","계약을 찾을 수 없습니다.");return value;}
    private Contract contract(UUID classroomId,UUID id,boolean lock){Contract value=query("select id,classroom_id,property_id,lease_listing_id,subscription_offer_id,subscription_application_id,contract_type,occupant_student_id,landlord_kind,landlord_student_id,deposit_amount,monthly_rent,sale_price,payment_cycle_days,contract_term_days,started_at,ends_at,next_payment_at,status from property_contracts where classroom_id=? and id=?"+(lock?" for update":""),rs->rs.next()?contract(rs):null,classroomId,id);if(value==null)throw error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","계약을 찾을 수 없습니다.");return value;}
    private static Contract contract(java.sql.ResultSet rs)throws java.sql.SQLException{return new Contract(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getObject(5,UUID.class),rs.getObject(6,UUID.class),rs.getString(7),rs.getObject(8,UUID.class),rs.getString(9),rs.getObject(10,UUID.class),rs.getLong(11),rs.getLong(12),rs.getLong(13),(Integer)rs.getObject(14),(Integer)rs.getObject(15),instant(rs,16),instant(rs,17),instant(rs,18),rs.getString(19));}
    private static Map<String,Object> contractView(Contract c){Map<String,Object> m=new LinkedHashMap<>();m.put("contract_id",c.id());m.put("property_id",c.propertyId());m.put("contract_type",c.contractType());m.put("occupant_student_id",c.occupantStudentId());m.put("landlord_kind",c.landlordKind());m.put("landlord_student_id",c.landlordStudentId());m.put("deposit_amount",c.depositAmount());m.put("monthly_rent",c.monthlyRent());m.put("sale_price",c.salePrice());m.put("started_at",c.startedAt());m.put("ends_at",c.endsAt());m.put("next_payment_at",c.nextPaymentAt());m.put("status",c.status());return m;}
    private Offer offerById(UUID id,boolean lock){Offer value=query("select id,classroom_id,title,application_opens_at,application_closes_at,sale_price,supply_quantity,minimum_deposit_count,minimum_balance,status from subscription_offers where id=?"+(lock?" for update":""),rs->rs.next()?offer(rs):null,id);if(value==null)throw error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","청약 매물을 찾을 수 없습니다.");return value;}
    private Offer offer(UUID classroomId,UUID id,boolean lock){Offer value=query("select id,classroom_id,title,application_opens_at,application_closes_at,sale_price,supply_quantity,minimum_deposit_count,minimum_balance,status from subscription_offers where classroom_id=? and id=?"+(lock?" for update":""),rs->rs.next()?offer(rs):null,classroomId,id);if(value==null)throw error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","청약 매물을 찾을 수 없습니다.");return value;}
    private static Offer offer(java.sql.ResultSet rs)throws java.sql.SQLException{return new Offer(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getTimestamp(4).toInstant(),rs.getTimestamp(5).toInstant(),rs.getLong(6),rs.getInt(7),rs.getInt(8),rs.getLong(9),rs.getString(10));}
    private static Map<String,Object> offerView(Offer o){return Map.of("offer_id",o.id(),"classroom_id",o.classroomId(),"title",o.title(),"opens_at",o.opensAt(),"closes_at",o.closesAt(),"sale_price",o.salePrice(),"supply_quantity",o.supplyQuantity(),"minimum_deposit_count",o.minimumDepositCount(),"minimum_balance",o.minimumBalance(),"status",o.status());}
    private void requireWinner(Property p,UUID studentId,UUID applicationId){if(applicationId==null||p.subscriptionWinnerStudentId()==null||!studentId.equals(p.subscriptionWinnerStudentId()))throw error(HttpStatus.FORBIDDEN,"SUBSCRIPTION_WINNER_REQUIRED","이 자리 청약에 당첨된 학생만 매매를 신청할 수 있습니다."); Application app=application(p.classroomId(),applicationId);if(!app.status().equals("WON")||!studentId.equals(app.studentId())||!p.id().equals(app.assignedPropertyId()))throw error(HttpStatus.FORBIDDEN,"SUBSCRIPTION_WINNER_REQUIRED","당첨 정보가 일치하지 않습니다.");}
    private long offerPrice(UUID offerId){return offerById(offerId,false).salePrice();}
    private Application application(UUID classroomId,UUID id){Application value=query("select id,classroom_id,subscription_offer_id,classroom_student_id,deposit_count_snapshot,deposit_amount_snapshot,assigned_property_id,status,applied_at from subscription_applications where classroom_id=? and id=?",rs->rs.next()?application(rs):null,classroomId,id);if(value==null)throw error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","청약 신청을 찾을 수 없습니다.");return value;}
    private Application applicationById(UUID id,boolean lock){Application value=query("select id,classroom_id,subscription_offer_id,classroom_student_id,deposit_count_snapshot,deposit_amount_snapshot,assigned_property_id,status,applied_at from subscription_applications where id=?"+(lock?" for update":""),rs->rs.next()?application(rs):null,id);if(value==null)throw error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","청약 신청을 찾을 수 없습니다.");return value;}
    private static Application application(java.sql.ResultSet rs)throws java.sql.SQLException{return new Application(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getInt(5),rs.getLong(6),rs.getObject(7,UUID.class),rs.getString(8),rs.getTimestamp(9).toInstant());}
    private static Map<String,Object> applicationView(Application a){Map<String,Object> m=new LinkedHashMap<>();m.put("application_id",a.id());m.put("offer_id",a.offerId());m.put("student_id",a.studentId());m.put("deposit_count",a.depositCount());m.put("deposit_amount",a.depositAmount());m.put("assigned_property_id",a.assignedPropertyId());m.put("status",a.status());m.put("applied_at",a.appliedAt());return m;}
    private SubscriptionAccount subscriptionAccount(UUID classroomId,UUID studentId,boolean lock){return query("select classroom_student_id,classroom_id,total_deposit_amount,deposit_count,last_deposited_at,auto_deposit_amount,auto_deposit_cycle_days,next_auto_deposit_at,status,state_version from subscription_accounts where classroom_id=? and classroom_student_id=?"+(lock?" for update":""),rs->rs.next()?subscriptionAccount(rs):null,classroomId,studentId);}
    private AutoDepositPolicy autoDepositPolicy(UUID classroomId){return query("select classroom_id,enabled,amount,cycle_days,state_version from subscription_auto_deposit_policies where classroom_id=?",rs->rs.next()?new AutoDepositPolicy(rs.getObject(1,UUID.class),rs.getBoolean(2),(Long)rs.getObject(3),(Integer)rs.getObject(4),rs.getLong(5)):new AutoDepositPolicy(classroomId,false,null,null,0),classroomId);}
    private static Map<String,Object> autoDepositPolicyView(AutoDepositPolicy policy){Map<String,Object> m=new LinkedHashMap<>();m.put("classroom_id",policy.classroomId());m.put("enabled",policy.enabled());m.put("amount",policy.amount());m.put("cycle_days",policy.cycleDays());m.put("state_version",policy.stateVersion());return m;}
    private static SubscriptionAccount subscriptionAccount(java.sql.ResultSet rs)throws java.sql.SQLException{return new SubscriptionAccount(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getLong(3),rs.getInt(4),instant(rs,5),(Long)rs.getObject(6),(Integer)rs.getObject(7),instant(rs,8),rs.getString(9),rs.getLong(10));}
    private static Map<String,Object> subscriptionView(SubscriptionAccount a){Map<String,Object> m=new LinkedHashMap<>();m.put("classroom_id",a.classroomId());m.put("student_id",a.studentId());m.put("total_deposit_amount",a.totalDepositAmount());m.put("deposit_count",a.depositCount());m.put("last_deposited_at",a.lastDepositedAt());m.put("auto_deposit_amount",a.autoAmount());m.put("auto_deposit_cycle_days",a.autoCycleDays());m.put("next_auto_deposit_at",a.nextAutoAt());m.put("status",a.status());m.put("state_version",a.stateVersion());return m;}
    /**
     * SQL 파라미터로 넘길 값이다.
     *
     * <p>PostgreSQL 드라이버는 {@link Instant}를 받으면 "Can't infer the SQL type"으로 거절한다.
     * 이 서비스는 시각을 모두 Instant로 다루므로, 넘기기 직전에 Timestamp로 바꿔 준다.
     * 이 변환이 없으면 자리 수정·청약 공고 열기 같은 쓰기 요청이 모두 500으로 끝난다.
     */
    private static Object[] sqlArgs(Object... args) {
        Object[] converted = new Object[args.length];
        for (int index = 0; index < args.length; index++) {
            converted[index] = args[index] instanceof Instant value ? Timestamp.from(value) : args[index];
        }
        return converted;
    }

    private int update(String sql, Object... args) {
        return jdbc.update(sql, sqlArgs(args));
    }

    private <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
        return jdbc.query(sql, mapper, sqlArgs(args));
    }

    /** 한 건만 꺼내 쓰는 조회다. 결과가 없으면 추출기가 null을 돌려준다. */
    private <T> T query(String sql, ResultSetExtractor<T> extractor, Object... args) {
        return jdbc.query(sql, extractor, sqlArgs(args));
    }

    private <T> T queryForObject(String sql, Class<T> type, Object... args) {
        return jdbc.queryForObject(sql, type, sqlArgs(args));
    }

    private static Instant instant(java.sql.ResultSet rs,int index)throws java.sql.SQLException{java.sql.Timestamp value=rs.getTimestamp(index);return value==null?null:value.toInstant();}

    public record PropertyInput(String coordinate,String displayName,Integer rowIndex,Integer columnIndex,String tier,boolean isPublic,boolean active,boolean allowsRent,boolean allowsJeonse,boolean allowsSale,boolean allowsSubscription,long salePrice,long depositAmount,long monthlyRent,int paymentCycleDays,int contractTermDays){}
    public record ContractInput(String contractType,UUID subscriptionApplicationId){}
    public record ListingInput(String leaseType,long depositAmount,long monthlyRent,int paymentCycleDays,int contractTermDays,boolean isPublic){}
    public record OfferInput(String title,Instant opensAt,Instant closesAt,long salePrice,int supplyQuantity,int minimumDepositCount,long minimumBalance,List<UUID> propertyIds){}
    private record Property(UUID id,UUID classroomId,String coordinate,String displayName,String tier,boolean isPublic,boolean active,boolean rent,boolean jeonse,boolean sale,boolean subscription,long salePrice,long depositAmount,long monthlyRent,int paymentCycleDays,int contractTermDays,String ownerKind,UUID ownerStudentId,String status,UUID subscriptionOfferId,UUID subscriptionWinnerStudentId){}
    private record Listing(UUID id,UUID classroomId,UUID propertyId,UUID landlordStudentId,String leaseType,long depositAmount,long monthlyRent,int paymentCycleDays,int contractTermDays,boolean isPublic,String status){}
    private record SaleListing(UUID id,UUID classroomId,UUID propertyId,UUID sellerStudentId,long salePrice,String status){}
    private record Contract(UUID id,UUID classroomId,UUID propertyId,UUID listingId,UUID subscriptionOfferId,UUID subscriptionApplicationId,String contractType,UUID occupantStudentId,String landlordKind,UUID landlordStudentId,long depositAmount,long monthlyRent,long salePrice,Integer paymentCycleDays,Integer contractTermDays,Instant startedAt,Instant endsAt,Instant nextPaymentAt,String status){}
    private record Offer(UUID id,UUID classroomId,String title,Instant opensAt,Instant closesAt,long salePrice,int supplyQuantity,int minimumDepositCount,long minimumBalance,String status){}
    private record Application(UUID id,UUID classroomId,UUID offerId,UUID studentId,int depositCount,long depositAmount,UUID assignedPropertyId,String status,Instant appliedAt){}
    private record SubscriptionAccount(UUID studentId,UUID classroomId,long totalDepositAmount,int depositCount,Instant lastDepositedAt,Long autoAmount,Integer autoCycleDays,Instant nextAutoAt,String status,long stateVersion){}
    private record AutoDepositPolicy(UUID classroomId,boolean enabled,Long amount,Integer cycleDays,long stateVersion){}
    private record DueRent(UUID paymentId,UUID classroomId,UUID contractId,UUID tenantId,String landlordKind,UUID landlordStudentId,long amount,int paymentCycleDays,Instant endsAt,Instant dueAt){}
}
