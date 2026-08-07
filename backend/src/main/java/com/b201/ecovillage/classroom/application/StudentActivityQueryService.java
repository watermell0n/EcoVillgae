package com.b201.ecovillage.classroom.application;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.identity.authorization.ClassroomAuthorizationService;
import com.b201.ecovillage.identity.authorization.ClassroomPermission;

@Service
public class StudentActivityQueryService {

    private static final String RECENT_ACTIVITIES_SQL = """
        select item.id as resource_id,
               store_order.classroom_student_id as student_id,
               'STORE_PURCHASE' as activity_type,
               product.name as title,
               item.quantity as quantity,
               item.total_amount as amount,
               cast(store_order.status as varchar) as status,
               store_order.created_at as occurred_at
          from store_order_items item
          join store_orders store_order
            on store_order.classroom_id = item.classroom_id
           and store_order.id = item.store_order_id
          join store_product_versions product_version
            on product_version.classroom_id = item.classroom_id
           and product_version.id = item.store_product_version_id
          join store_products product
            on product.classroom_id = product_version.classroom_id
           and product.id = product_version.store_product_id
         where store_order.classroom_id = ?
        union all
        select account.id,
               account.classroom_student_id,
               'DEPOSIT_OPEN',
               product.name,
               cast(null as integer),
               account.principal_amount,
               cast(account.status as varchar),
               account.opened_at
          from deposit_accounts account
          join financial_product_versions product_version
            on product_version.classroom_id = account.classroom_id
           and product_version.id = account.product_version_id
          join financial_products product
            on product.classroom_id = product_version.classroom_id
           and product.id = product_version.financial_product_id
         where account.classroom_id = ?
        union all
        select account.id,
               account.classroom_student_id,
               'SAVINGS_OPEN',
               product.name,
               account.installment_count,
               account.installment_amount,
               cast(account.status as varchar),
               account.opened_at
          from savings_accounts account
          join financial_product_versions product_version
            on product_version.classroom_id = account.classroom_id
           and product_version.id = account.product_version_id
          join financial_products product
            on product.classroom_id = product_version.classroom_id
           and product.id = product_version.financial_product_id
         where account.classroom_id = ?
        union all
        select investment_order.id,
               investment_order.classroom_student_id,
               case when investment_order.side = 'BUY' then 'INVESTMENT_BUY' else 'INVESTMENT_SELL' end,
               asset.name,
               investment_order.quantity,
               coalesce(investment_order.executed_price, 0) * investment_order.quantity,
               cast(investment_order.status as varchar),
               investment_order.submitted_at
          from investment_orders investment_order
          join investment_assets asset
            on asset.classroom_id = investment_order.classroom_id
           and asset.id = investment_order.investment_asset_id
         where investment_order.classroom_id = ?
         order by occurred_at desc
         limit ?
        """;

    private final ClassroomAuthorizationService authorizationService;
    private final JdbcTemplate jdbcTemplate;

    public StudentActivityQueryService(
        ClassroomAuthorizationService authorizationService,
        JdbcTemplate jdbcTemplate
    ) {
        this.authorizationService = authorizationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public List<StudentActivityView> findRecent(
        String subject,
        UUID classroomId,
        int requestedLimit
    ) {
        authorizationService.authorize(
            subject,
            classroomId,
            ClassroomPermission.READ_CLASSROOM_REPORT,
            null
        );
        int limit = Math.max(1, Math.min(requestedLimit, 200));
        return jdbcTemplate.query(
            RECENT_ACTIVITIES_SQL,
            (resultSet, rowNumber) -> {
                Timestamp occurredAt = resultSet.getTimestamp("occurred_at");
                return new StudentActivityView(
                    resultSet.getObject("resource_id", UUID.class),
                    resultSet.getObject("student_id", UUID.class),
                    resultSet.getString("activity_type"),
                    resultSet.getString("title"),
                    resultSet.getObject("quantity", Integer.class),
                    resultSet.getLong("amount"),
                    resultSet.getString("status"),
                    occurredAt.toInstant()
                );
            },
            classroomId,
            classroomId,
            classroomId,
            classroomId,
            limit
        );
    }
}
