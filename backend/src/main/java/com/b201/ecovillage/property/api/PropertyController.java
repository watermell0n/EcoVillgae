package com.b201.ecovillage.property.api;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.b201.ecovillage.common.api.ApiMeta;
import com.b201.ecovillage.common.api.ApiResponse;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.property.application.PropertyService;
import com.fasterxml.jackson.annotation.JsonProperty;

@RestController
@RequestMapping
public class PropertyController {

    private final PropertyService properties;
    private final UuidV7Generator uuids;
    private final Clock clock;

    public PropertyController(PropertyService properties, UuidV7Generator uuids, Clock clock) {
        this.properties = properties;
        this.uuids = uuids;
        this.clock = clock;
    }

    @GetMapping("/classrooms/{classroomId}/property-layout")
    public ResponseEntity<ApiResponse<Map<String, Object>>> layout(@PathVariable UUID classroomId, Principal principal) {
        return response(HttpStatus.OK, properties.layout(subject(principal), classroomId));
    }

    @PutMapping("/classrooms/{classroomId}/property-layout")
    public ResponseEntity<ApiResponse<Map<String, Object>>> replaceLayout(
        @PathVariable UUID classroomId, @Valid @RequestBody LayoutRequest request, Principal principal
    ) {
        return response(HttpStatus.OK, properties.replaceLayout(subject(principal), classroomId, request.rows(), request.columns()));
    }

    @PostMapping("/classrooms/{classroomId}/properties")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createProperty(
        @PathVariable UUID classroomId, @Valid @RequestBody PropertyRequest request, Principal principal
    ) {
        return response(HttpStatus.CREATED, properties.createProperty(subject(principal), classroomId, request.input()));
    }

    @PutMapping("/properties/{propertyId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateProperty(
        @PathVariable UUID propertyId, @Valid @RequestBody PropertyUpdateRequest request, Principal principal
    ) {
        return response(HttpStatus.OK, properties.updateProperty(subject(principal), propertyId, request.input()));
    }

    @DeleteMapping("/properties/{propertyId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteProperty(@PathVariable UUID propertyId, Principal principal) {
        properties.deactivateProperty(subject(principal), propertyId);
        return response(HttpStatus.OK, Map.of("property_id", propertyId, "status", "INACTIVE"));
    }

    @GetMapping("/classrooms/{classroomId}/properties/available")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> available(@PathVariable UUID classroomId, Principal principal) {
        return response(HttpStatus.OK, properties.available(subject(principal), classroomId));
    }

    @GetMapping("/students/me/properties")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myProperties(
        @RequestParam("classroom_id") UUID classroomId, Principal principal
    ) {
        return response(HttpStatus.OK, properties.mine(subject(principal), classroomId));
    }

    @GetMapping("/students/me/property-contracts")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> myPropertyContracts(
        @RequestParam("classroom_id") UUID classroomId, Principal principal
    ) {
        return response(HttpStatus.OK, properties.contractsForStudent(subject(principal), classroomId));
    }

    @GetMapping("/classrooms/{classroomId}/property-contracts")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> pendingContracts(@PathVariable UUID classroomId, Principal principal) {
        return response(HttpStatus.OK, properties.contractsForClassroom(subject(principal), classroomId));
    }

    @PostMapping("/properties/{propertyId}/contracts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> requestContract(
        @PathVariable UUID propertyId, @Valid @RequestBody ContractRequest request, Principal principal
    ) {
        return response(HttpStatus.CREATED, properties.requestContract(subject(principal), propertyId, new PropertyService.ContractInput(request.contractType(), request.subscriptionApplicationId())));
    }

    @PostMapping("/contracts/{contractId}/approve")
    public ResponseEntity<ApiResponse<Map<String, Object>>> approve(
        @PathVariable UUID contractId, @RequestHeader("Idempotency-Key") UUID idempotencyKey,
        @Valid @RequestBody(required = false) ContractDecisionRequest request, Principal principal
    ) {
        String reason = request == null ? null : request.reason();
        return response(HttpStatus.OK, properties.decideContract(subject(principal), contractId, true, reason, idempotencyKey.toString()));
    }

    @PostMapping("/contracts/{contractId}/reject")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reject(
        @PathVariable UUID contractId, @Valid @RequestBody(required = false) ContractDecisionRequest request, Principal principal
    ) {
        String reason = request == null ? null : request.reason();
        return response(HttpStatus.OK, properties.decideContract(subject(principal), contractId, false, reason, "property-reject-" + contractId));
    }

    @PostMapping("/contracts/{contractId}/terminate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> terminate(
        @PathVariable UUID contractId, @RequestHeader("Idempotency-Key") UUID idempotencyKey,
        @Valid @RequestBody(required = false) CommandMetadataRequest request, Principal principal
    ) {
        return response(HttpStatus.OK, properties.terminate(subject(principal), contractId, idempotencyKey.toString()));
    }

    @PostMapping("/students/me/properties/{propertyId}/lease-listings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createListing(
        @PathVariable UUID propertyId, @Valid @RequestBody LeaseListingRequest request, Principal principal
    ) {
        return response(HttpStatus.CREATED, properties.createListing(subject(principal), propertyId, request.input()));
    }

    @PutMapping("/lease-listings/{listingId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateListing(
        @PathVariable UUID listingId, @Valid @RequestBody LeaseListingRequest request, Principal principal
    ) {
        return response(HttpStatus.OK, properties.updateListing(subject(principal), listingId, request.input()));
    }

    @DeleteMapping("/lease-listings/{listingId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> deleteListing(@PathVariable UUID listingId, Principal principal) {
        properties.deleteListing(subject(principal), listingId);
        return response(HttpStatus.OK, Map.of("listing_id", listingId, "status", "CLOSED"));
    }

    @PostMapping("/students/me/properties/{propertyId}/sale-listing")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createSaleListing(
        @PathVariable UUID propertyId, @Valid @RequestBody SaleListingRequest request, Principal principal
    ) {
        return response(HttpStatus.CREATED, properties.createSaleListing(subject(principal), propertyId, request.salePrice()));
    }

    @DeleteMapping("/students/me/properties/{propertyId}/sale-listing")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelSaleListing(@PathVariable UUID propertyId, Principal principal) {
        properties.cancelSaleListing(subject(principal), propertyId);
        return response(HttpStatus.OK, Map.of("property_id", propertyId, "status", "CLOSED"));
    }

    @PostMapping("/classrooms/{classroomId}/subscription-offers")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createOffer(
        @PathVariable UUID classroomId, @Valid @RequestBody SubscriptionOfferRequest request, Principal principal
    ) {
        return response(HttpStatus.CREATED, properties.createOffer(subject(principal), classroomId, request.input()));
    }

    @GetMapping("/classrooms/{classroomId}/subscription-offers")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> offers(@PathVariable UUID classroomId, Principal principal) {
        return response(HttpStatus.OK, properties.offers(subject(principal), classroomId));
    }

    @PostMapping("/subscription-offers/{offerId}/cancel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelOffer(@PathVariable UUID offerId, Principal principal) {
        properties.cancelOffer(subject(principal), offerId);
        return response(HttpStatus.OK, Map.of("offer_id", offerId, "status", "CANCELED"));
    }

    @PostMapping("/subscription-offers/{offerId}/apply")
    public ResponseEntity<ApiResponse<Map<String, Object>>> applyOffer(
        @PathVariable UUID offerId, @Valid @RequestBody(required = false) CommandMetadataRequest request, Principal principal
    ) {
        return response(HttpStatus.CREATED, properties.applyOffer(subject(principal), offerId));
    }

    @PostMapping("/subscription-offers/{offerId}/draw")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> drawOffer(
        @PathVariable UUID offerId, @Valid @RequestBody(required = false) CommandMetadataRequest request, Principal principal
    ) {
        return response(HttpStatus.OK, properties.drawOffer(subject(principal), offerId));
    }

    @GetMapping("/students/me/subscription-applications")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> applications(
        @RequestParam("classroom_id") UUID classroomId, Principal principal
    ) {
        return response(HttpStatus.OK, properties.applications(subject(principal), classroomId));
    }

    @DeleteMapping("/students/me/subscription-applications/{applicationId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> cancelApplication(@PathVariable UUID applicationId, Principal principal) {
        properties.cancelApplication(subject(principal), applicationId);
        return response(HttpStatus.OK, Map.of("application_id", applicationId, "status", "CANCELED"));
    }

    @PostMapping("/students/me/subscription-account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> openSubscriptionAccount(
        @Valid @RequestBody SubscriptionAccountRequest request, Principal principal
    ) {
        return response(HttpStatus.CREATED, properties.openSubscriptionAccount(subject(principal), request.classroomId()));
    }

    @PutMapping("/classrooms/{classroomId}/subscription-auto-deposit-policy")
    public ResponseEntity<ApiResponse<Map<String, Object>>> updateSubscriptionAutoDepositPolicy(
        @PathVariable UUID classroomId, @Valid @RequestBody SubscriptionAutoDepositPolicyRequest request, Principal principal
    ) {
        return response(HttpStatus.OK, properties.updateAutoDepositPolicy(subject(principal), classroomId, request.enabled(), request.amount(), request.cycleDays()));
    }

    @PostMapping("/students/me/subscription-account/deposits")
    public ResponseEntity<ApiResponse<Map<String, Object>>> depositSubscription(
        @RequestHeader("Idempotency-Key") UUID idempotencyKey, @Valid @RequestBody SubscriptionDepositRequest request, Principal principal
    ) {
        return response(HttpStatus.CREATED, properties.depositSubscription(subject(principal), request.classroomId(), request.amount(), idempotencyKey.toString()));
    }

    @GetMapping("/students/me/subscription-account")
    public ResponseEntity<ApiResponse<Map<String, Object>>> subscriptionAccount(
        @RequestParam("classroom_id") UUID classroomId, Principal principal
    ) {
        return response(HttpStatus.OK, properties.subscriptionAccountView(subject(principal), classroomId));
    }

    private <T> ResponseEntity<ApiResponse<T>> response(HttpStatus status, T data) {
        UUID requestId = uuids.generate();
        return ResponseEntity.status(status).body(new ApiResponse<>(data, new ApiMeta(requestId, uuids.generate(), clock.instant())));
    }

    private static String subject(Principal principal) {
        return principal == null ? null : principal.getName();
    }

    public record CommandMetadataRequest(
        @JsonProperty("expected_state_version") Long expectedStateVersion, @JsonProperty("screen_id") String screenId,
        @JsonProperty("ui_action_id") String uiActionId, @JsonProperty("client_occurred_at") Instant clientOccurredAt
    ) { }

    public record LayoutRequest(
        @JsonProperty("expected_state_version") Long expectedStateVersion, @JsonProperty("screen_id") String screenId,
        @JsonProperty("ui_action_id") String uiActionId, @JsonProperty("client_occurred_at") Instant clientOccurredAt,
        @Min(1) @JsonProperty("rows") int rows, @Min(1) @JsonProperty("columns") int columns
    ) { }

    public record PropertyRequest(
        @JsonProperty("expected_state_version") Long expectedStateVersion, @JsonProperty("screen_id") String screenId,
        @JsonProperty("ui_action_id") String uiActionId, @JsonProperty("client_occurred_at") Instant clientOccurredAt,
        @JsonProperty("coordinate") String coordinate, @JsonProperty("display_name") String displayName,
        @JsonProperty("row_index") Integer rowIndex, @JsonProperty("column_index") Integer columnIndex,
        @JsonProperty("tier") String tier, @JsonProperty("is_public") boolean isPublic,
        @JsonProperty("active") boolean active, @JsonProperty("allows_rent") boolean allowsRent,
        @JsonProperty("allows_jeonse") boolean allowsJeonse, @JsonProperty("allows_sale") boolean allowsSale,
        @JsonProperty("allows_subscription") boolean allowsSubscription,
        @Min(0) @JsonProperty("sale_price") long salePrice, @Min(0) @JsonProperty("deposit_amount") long depositAmount,
        @Min(0) @JsonProperty("rent_deposit_amount") long rentDepositAmount,
        @Min(0) @JsonProperty("monthly_rent") long monthlyRent,
        @Min(1) @JsonProperty("payment_cycle_days") int paymentCycleDays,
        @Min(1) @JsonProperty("contract_term_days") int contractTermDays
    ) {
        PropertyService.PropertyInput input() { return new PropertyService.PropertyInput(coordinate, displayName, rowIndex, columnIndex, tier, isPublic, active, allowsRent, allowsJeonse, allowsSale, allowsSubscription, salePrice, depositAmount, rentDepositAmount, monthlyRent, paymentCycleDays, contractTermDays); }
    }

    public record PropertyUpdateRequest(
        @JsonProperty("expected_state_version") Long expectedStateVersion, @JsonProperty("screen_id") String screenId,
        @JsonProperty("ui_action_id") String uiActionId, @JsonProperty("client_occurred_at") Instant clientOccurredAt,
        @JsonProperty("display_name") String displayName, @JsonProperty("tier") String tier,
        @JsonProperty("is_public") boolean isPublic, @JsonProperty("active") boolean active,
        @JsonProperty("allows_rent") boolean allowsRent, @JsonProperty("allows_jeonse") boolean allowsJeonse,
        @JsonProperty("allows_sale") boolean allowsSale, @JsonProperty("allows_subscription") boolean allowsSubscription,
        @Min(0) @JsonProperty("sale_price") long salePrice, @Min(0) @JsonProperty("deposit_amount") long depositAmount,
        @Min(0) @JsonProperty("rent_deposit_amount") long rentDepositAmount,
        @Min(0) @JsonProperty("monthly_rent") long monthlyRent,
        @Min(1) @JsonProperty("payment_cycle_days") int paymentCycleDays,
        @Min(1) @JsonProperty("contract_term_days") int contractTermDays
    ) {
        PropertyService.PropertyInput input() { return new PropertyService.PropertyInput(null, displayName, null, null, tier, isPublic, active, allowsRent, allowsJeonse, allowsSale, allowsSubscription, salePrice, depositAmount, rentDepositAmount, monthlyRent, paymentCycleDays, contractTermDays); }
    }

    public record ContractRequest(
        @JsonProperty("expected_state_version") Long expectedStateVersion, @JsonProperty("screen_id") String screenId,
        @JsonProperty("ui_action_id") String uiActionId, @JsonProperty("client_occurred_at") Instant clientOccurredAt,
        @NotBlank @JsonProperty("contract_type") String contractType, @JsonProperty("subscription_application_id") UUID subscriptionApplicationId
    ) { }
    public record ContractDecisionRequest(
        @JsonProperty("expected_state_version") Long expectedStateVersion, @JsonProperty("screen_id") String screenId,
        @JsonProperty("ui_action_id") String uiActionId, @JsonProperty("client_occurred_at") Instant clientOccurredAt,
        @JsonProperty("reason") String reason
    ) { }

    public record LeaseListingRequest(
        @JsonProperty("expected_state_version") Long expectedStateVersion, @JsonProperty("screen_id") String screenId,
        @JsonProperty("ui_action_id") String uiActionId, @JsonProperty("client_occurred_at") Instant clientOccurredAt,
        @NotBlank @JsonProperty("lease_type") String leaseType, @Min(0) @JsonProperty("deposit_amount") long depositAmount,
        @Min(0) @JsonProperty("monthly_rent") long monthlyRent, @Min(1) @JsonProperty("payment_cycle_days") int paymentCycleDays,
        @Min(1) @JsonProperty("contract_term_days") int contractTermDays, @JsonProperty("is_public") boolean isPublic
    ) { PropertyService.ListingInput input() { return new PropertyService.ListingInput(leaseType, depositAmount, monthlyRent, paymentCycleDays, contractTermDays, isPublic); } }

    public record SaleListingRequest(
        @JsonProperty("expected_state_version") Long expectedStateVersion, @JsonProperty("screen_id") String screenId,
        @JsonProperty("ui_action_id") String uiActionId, @JsonProperty("client_occurred_at") Instant clientOccurredAt,
        @Min(1) @JsonProperty("sale_price") long salePrice
    ) { }

    public record SubscriptionOfferRequest(
        @JsonProperty("expected_state_version") Long expectedStateVersion, @JsonProperty("screen_id") String screenId,
        @JsonProperty("ui_action_id") String uiActionId, @JsonProperty("client_occurred_at") Instant clientOccurredAt,
        @NotBlank @JsonProperty("title") String title, @NotNull @JsonProperty("opens_at") Instant opensAt,
        @NotNull @JsonProperty("closes_at") Instant closesAt, @Min(1) @JsonProperty("sale_price") long salePrice,
        @Min(1) @JsonProperty("supply_quantity") int supplyQuantity, @Min(0) @JsonProperty("minimum_deposit_count") int minimumDepositCount,
        @Min(0) @JsonProperty("minimum_balance") long minimumBalance, @NotNull @JsonProperty("property_ids") List<UUID> propertyIds
    ) { PropertyService.OfferInput input() { return new PropertyService.OfferInput(title, opensAt, closesAt, salePrice, supplyQuantity, minimumDepositCount, minimumBalance, propertyIds); } }

    public record SubscriptionAccountRequest(
        @JsonProperty("expected_state_version") Long expectedStateVersion, @JsonProperty("screen_id") String screenId,
        @JsonProperty("ui_action_id") String uiActionId, @JsonProperty("client_occurred_at") Instant clientOccurredAt,
        @NotNull @JsonProperty("classroom_id") UUID classroomId
    ) { }
    public record SubscriptionAutoDepositPolicyRequest(
        @JsonProperty("expected_state_version") Long expectedStateVersion, @JsonProperty("screen_id") String screenId,
        @JsonProperty("ui_action_id") String uiActionId, @JsonProperty("client_occurred_at") Instant clientOccurredAt,
        @JsonProperty("enabled") boolean enabled, @JsonProperty("amount") Long amount, @JsonProperty("cycle_days") Integer cycleDays
    ) { }
    public record SubscriptionDepositRequest(
        @JsonProperty("expected_state_version") Long expectedStateVersion, @JsonProperty("screen_id") String screenId,
        @JsonProperty("ui_action_id") String uiActionId, @JsonProperty("client_occurred_at") Instant clientOccurredAt,
        @NotNull @JsonProperty("classroom_id") UUID classroomId, @Min(1) @JsonProperty("amount") long amount
    ) { }
}
