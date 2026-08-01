package com.b201.ecovillage.ai.application;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.b201.ecovillage.ai.contract.BalancingContract;
import com.b201.ecovillage.ai.infrastructure.AiMonitorProperties;
import com.b201.ecovillage.identity.domain.Classroom;
import com.b201.ecovillage.identity.domain.ClassroomStatus;
import com.b201.ecovillage.identity.repository.ClassroomRepository;

@Component
public class AutoMonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoMonitorScheduler.class);
    private static final Set<BalancingContract.MetricCode> DEFAULT_METRICS = Set.of(
        BalancingContract.MetricCode.LIQUID_MONEY_CHANGE_RATE_BPS,
        BalancingContract.MetricCode.ISSUANCE_RECOVERY_RATE_BPS,
        BalancingContract.MetricCode.ASSET_CONCENTRATION_TOP20_BPS,
        BalancingContract.MetricCode.CONSUMPTION_RATE_BPS,
        BalancingContract.MetricCode.PRODUCT_SELL_THROUGH_RATE_BPS,
        BalancingContract.MetricCode.PRODUCT_STOCKOUT_SHARE_BPS,
        BalancingContract.MetricCode.PAYMENT_MISS_RATE_BPS,
        BalancingContract.MetricCode.REWARD_ISSUANCE_SHARE_BPS
    );

    private final ClassroomRepository classroomRepository;
    private final OperationalAnalysisService analysisService;
    private final AiMonitorProperties properties;
    private final Clock clock;

    public AutoMonitorScheduler(
        ClassroomRepository classroomRepository,
        OperationalAnalysisService analysisService,
        AiMonitorProperties properties,
        Clock clock
    ) {
        this.classroomRepository = classroomRepository;
        this.analysisService = analysisService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${ecovillage.ai-monitor.cron:0 5 0 * * *}", zone = "UTC")
    public void monitorCompletedSevenDayPeriods() {
        if (!properties.isEnabled()) {
            return;
        }
        for (Classroom classroom : classroomRepository.findAll()) {
            ScheduledPeriod period = scheduledPeriod(classroom, clock.instant());
            if (period == null) {
                continue;
            }
            String key = String.join(
                ":",
                classroom.getId().toString(),
                period.from().toString(),
                period.to().toString(),
                BalancingContract.METRIC_VERSION,
                BalancingContract.THRESHOLD_VERSION
            );
            UUID requestId = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
            UUID correlationId = UUID.nameUUIDFromBytes(
                ("correlation:" + key).getBytes(StandardCharsets.UTF_8)
            );
            try {
                analysisService.executeSystem(new OperationalAnalysisCommand(
                    null,
                    classroom.getId(),
                    requestId,
                    correlationId,
                    BalancingContract.AnalysisMode.AUTO_MONITOR,
                    period.from(),
                    period.to(),
                    DEFAULT_METRICS
                ));
            } catch (RuntimeException exception) {
                log.warn(
                    "AUTO_MONITOR failed for classroom {} and period {}..{}",
                    classroom.getId(),
                    period.from(),
                    period.to(),
                    exception
                );
            }
        }
    }

    ScheduledPeriod scheduledPeriod(Classroom classroom, Instant now) {
        if (
            classroom.getStatus() != ClassroomStatus.ACTIVE
                || classroom.getActualStartsOn() == null
        ) {
            return null;
        }
        ZoneId zone = ZoneId.of(classroom.getTimeZone());
        LocalDate localToday = now.atZone(zone).toLocalDate();
        LocalDate start = classroom.getActualStartsOn();
        long elapsedDays = ChronoUnit.DAYS.between(start, localToday);
        if (elapsedDays < 7) {
            return null;
        }
        long completedPeriods = elapsedDays / 7;
        LocalDate endDate = start.plusDays(completedPeriods * 7);
        if (classroom.getActualEndsOn() != null && endDate.isAfter(classroom.getActualEndsOn().plusDays(1))) {
            endDate = classroom.getActualEndsOn().plusDays(1);
        }
        LocalDate fromDate = endDate.minusDays(7);
        if (fromDate.isBefore(start)) {
            return null;
        }
        return new ScheduledPeriod(
            fromDate.atStartOfDay(zone).toInstant(),
            endDate.atStartOfDay(zone).toInstant()
        );
    }

    record ScheduledPeriod(Instant from, Instant to) {
    }
}
