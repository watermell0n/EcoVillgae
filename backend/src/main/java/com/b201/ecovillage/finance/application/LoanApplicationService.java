package com.b201.ecovillage.finance.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.b201.ecovillage.classroom.application.ClassroomOperationException;
import com.b201.ecovillage.classroom.domain.ClassroomPolicyVersion;
import com.b201.ecovillage.classroom.repository.ClassroomModuleRepository;
import com.b201.ecovillage.classroom.repository.ClassroomPolicyVersionRepository;
import com.b201.ecovillage.common.id.UuidV7Generator;
import com.b201.ecovillage.finance.domain.LoanPolicyVersion;
import com.b201.ecovillage.finance.domain.LoanCreditPolicy;
import com.b201.ecovillage.finance.domain.LoanRepaymentPlan;
import com.b201.ecovillage.finance.repository.LoanPolicyVersionRepository;
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
import com.b201.ecovillage.realtime.application.RealtimeEventPublisher;

@Service
public class LoanApplicationService {
    private final ClassroomAuthorizationService authorization; private final ClassroomModuleRepository modules;
    private final LoanPolicyVersionRepository policies; private final ClassroomPolicyVersionRepository classroomPolicies; private final UserAccountRepository users; private final LedgerAccountRepository ledger;
    private final AtomicLedgerTransactionService transactions; private final JdbcTemplate jdbc; private final UuidV7Generator uuids; private final Clock clock; private final RealtimeEventPublisher events;
    public LoanApplicationService(ClassroomAuthorizationService authorization, ClassroomModuleRepository modules, LoanPolicyVersionRepository policies, ClassroomPolicyVersionRepository classroomPolicies, UserAccountRepository users, LedgerAccountRepository ledger, AtomicLedgerTransactionService transactions, JdbcTemplate jdbc, UuidV7Generator uuids, Clock clock, RealtimeEventPublisher events) {
        this.authorization=authorization; this.modules=modules; this.policies=policies; this.classroomPolicies=classroomPolicies; this.users=users; this.ledger=ledger; this.transactions=transactions; this.jdbc=jdbc; this.uuids=uuids; this.clock=clock; this.events=events;
    }

    @Transactional
    public LoanApplicationView apply(String subject, UUID classroomId, long amount, Integer requestedInstallments) {
        UUID studentId = student(subject); authorization.authorize(subject, classroomId, ClassroomPermission.REQUEST_LOAN, studentId);
        if (!modules.existsByClassroomIdAndModuleCodeAndStatus(classroomId, "LOAN", "ACTIVE")) throw error(HttpStatus.CONFLICT,"MODULE_NOT_ACTIVE","대출 모듈이 활성화되지 않았습니다.");
        String studentNickname=jdbc.queryForObject("select nickname from classroom_students where classroom_id=? and id=? for update",String.class,classroomId,studentId);
        Credit credit = credit(classroomId, studentId); LoanPolicyVersion policy = currentPolicy(classroomId);
        if ("RESTRICTED".equals(credit.level())) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"CREDIT_RESTRICTED","현재 신용 상태에서는 새 대출을 신청할 수 없습니다.");
        if (requestedInstallments != null && requestedInstallments != policy.getInstallmentCount()) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_INSTALLMENT_COUNT","상환 회차는 현재 대출 정책과 같아야 합니다.");
        long limit = LoanCreditPolicy.availableLimit(policy.getMaximumPrincipal(), credit.score());
        if (amount < policy.getInstallmentCount() || amount > limit) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"LOAN_LIMIT_EXCEEDED","신청 금액은 상환 회차 이상이며 신용점수별 한도 이하여야 합니다.");
        Integer active = jdbc.queryForObject("select count(*) from loan_applications a left join loans l on l.loan_application_id=a.id where a.classroom_id=? and a.classroom_student_id=? and (a.status='APPLIED' or l.status in ('ACTIVE','OVERDUE'))", Integer.class, classroomId, studentId);
        if (active != null && active > 0) throw error(HttpStatus.CONFLICT,"ACTIVE_LOAN_APPLICATION_EXISTS","처리 중인 대출 신청이 있습니다.");
        UUID id=uuids.generate(); Instant now=clock.instant();
        jdbc.update("insert into loan_applications(id,classroom_id,classroom_student_id,loan_policy_version_id,requested_amount,credit_level_snapshot,credit_score_snapshot,status,status_changed_at,created_at,updated_at) values(?,?,?,?,?,?,?,'APPLIED',?,?,?)", id,classroomId,studentId,policy.getId(),amount,credit.level(),credit.score(),now,now,now);
        LoanApplicationView result=new LoanApplicationView(id, studentId, studentNickname, amount, null, credit.score(), credit.level(), "APPLIED", now); publishApplication(classroomId,result,0); return result;
    }

    @Transactional
    public LoanApplicationView cancel(String subject, UUID classroomId, UUID applicationId) {
        UUID studentId=student(subject); authorization.authorize(subject,classroomId,ClassroomPermission.REQUEST_LOAN,studentId); Instant now=clock.instant();
        LoanApplicationView row=jdbc.query("select a.id,a.classroom_student_id,s.nickname,a.requested_amount,a.approved_amount,a.credit_score_snapshot,a.credit_level_snapshot,a.status,a.created_at from loan_applications a join classroom_students s on s.id=a.classroom_student_id where a.classroom_id=? and a.id=? and a.classroom_student_id=? for update",rs->{if(!rs.next())return null;return application(rs);},classroomId,applicationId,studentId);
        if(row==null)throw error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","대출 신청을 찾을 수 없습니다.");
        if(!"APPLIED".equals(row.status()))throw error(HttpStatus.CONFLICT,"INVALID_STATE_TRANSITION","승인 대기 중인 신청만 취소할 수 있습니다.");
        jdbc.update("update loan_applications set status='CANCELED',state_version=state_version+1,status_changed_at=?,updated_at=? where id=?",now,now,applicationId);
        LoanApplicationView result=new LoanApplicationView(row.applicationId(),row.studentId(),row.studentNickname(),row.requestedAmount(),null,row.creditScore(),row.creditLevel(),"CANCELED",row.createdAt()); publishApplication(classroomId,result,1); return result;
    }

    @Transactional(readOnly=true)
    public List<LoanApplicationView> queue(String subject, UUID classroomId) {
        authorization.authorize(subject,classroomId,ClassroomPermission.MANAGE_LOANS,null);
        return jdbc.query("select a.id,a.classroom_student_id,s.nickname,a.requested_amount,a.approved_amount,a.credit_score_snapshot,a.credit_level_snapshot,a.status,a.created_at from loan_applications a join classroom_students s on s.id=a.classroom_student_id where a.classroom_id=? and a.status='APPLIED' order by a.created_at", (rs,n)->application(rs), classroomId);
    }

    @Transactional
    public LoanOverviewView mine(String subject, UUID classroomId) {
        UUID studentId=student(subject); authorization.authorize(subject,classroomId,ClassroomPermission.REQUEST_LOAN,studentId); Credit value=credit(classroomId,studentId); LoanPolicyVersion policy=currentPolicy(classroomId);
        List<LoanView> loans=jdbc.query("select l.id,l.principal_amount,l.outstanding_principal,l.outstanding_interest,l.status,l.matures_at from loans l join loan_applications a on a.id=l.loan_application_id where l.classroom_id=? and a.classroom_student_id=? order by l.started_at desc",(rs,n)->new LoanView(rs.getObject(1,UUID.class),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getString(5),rs.getTimestamp(6).toInstant()),classroomId,studentId);
        Pending pending=jdbc.query("select id,status from loan_applications where classroom_id=? and classroom_student_id=? and status='APPLIED' order by created_at desc limit 1",rs->rs.next()?new Pending(rs.getObject(1,UUID.class),rs.getString(2)):null,classroomId,studentId);
        boolean activeLoan=loans.stream().anyMatch(loan->"ACTIVE".equals(loan.status())||"OVERDUE".equals(loan.status())); long available=LoanCreditPolicy.availableLimit(policy.getMaximumPrincipal(),value.score()); boolean canApply=pending==null&&!activeLoan&&available>0&&!"RESTRICTED".equals(value.level());
        return new LoanOverviewView(value.score(),value.level(),available,policy.getInterestRateBps(),policy.getTermUnits(),policy.getInstallmentCount(),canApply,pending==null?null:pending.id(),pending==null?null:pending.status(),loans);
    }

    @Transactional
    public LoanApplicationView decide(String subject, UUID classroomId, UUID applicationId, boolean approve, Long approvedAmount, String reason) {
        authorization.authorize(subject,classroomId,ClassroomPermission.MANAGE_LOANS,null);
        UUID teacherId=users.findByExternalSubject(subject).orElseThrow().getId(); Instant now=clock.instant();
        LoanRow row=jdbc.query("select a.classroom_student_id,a.loan_policy_version_id,a.requested_amount,a.credit_score_snapshot,a.credit_level_snapshot,a.status,s.nickname,a.created_at from loan_applications a join classroom_students s on s.id=a.classroom_student_id where a.classroom_id=? and a.id=? for update", rs->{if(!rs.next())return null;return new LoanRow(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getLong(3),rs.getInt(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getTimestamp(8).toInstant());},classroomId,applicationId);
        if(row==null)throw error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","대출 신청을 찾을 수 없습니다."); if(!"APPLIED".equals(row.status()))throw error(HttpStatus.CONFLICT,"INVALID_STATE_TRANSITION","이미 처리된 신청입니다.");
        if(!approve){jdbc.update("update loan_applications set status='REJECTED',decision_reason=?,decided_by_user_id=?,decided_at=?,state_version=state_version+1,status_changed_at=?,updated_at=? where id=?",reason,teacherId,now,now,now,applicationId);LoanApplicationView result=new LoanApplicationView(applicationId,row.studentId(),row.nickname(),row.requested(),null,row.score(),row.level(),"REJECTED",row.createdAt());publishApplication(classroomId,result,1);return result;}
        if (!modules.existsByClassroomIdAndModuleCodeAndStatus(classroomId, "LOAN", "ACTIVE")) throw error(HttpStatus.CONFLICT,"MODULE_NOT_ACTIVE","대출 모듈이 활성화되지 않았습니다.");
        LoanPolicyVersion policy=policies.findById(row.policyId()).orElseThrow(); long amount=approvedAmount==null?row.requested():approvedAmount;
        Credit latest=credit(classroomId,row.studentId());
        if("RESTRICTED".equals(latest.level()))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"CREDIT_RESTRICTED","학생의 현재 신용 상태에서는 승인할 수 없습니다.");
        Integer activeLoans=jdbc.queryForObject("select count(*) from loans l join loan_applications a on a.id=l.loan_application_id where l.classroom_id=? and a.classroom_student_id=? and l.status in ('ACTIVE','OVERDUE')",Integer.class,classroomId,row.studentId());
        if(activeLoans!=null&&activeLoans>0)throw error(HttpStatus.CONFLICT,"ACTIVE_LOAN_EXISTS","이미 상환 중인 대출이 있습니다.");
        if(amount<policy.getInstallmentCount()||amount>row.requested()||amount>LoanCreditPolicy.availableLimit(policy.getMaximumPrincipal(),latest.score()))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"LOAN_LIMIT_EXCEEDED","승인 금액이 허용 범위를 벗어났습니다.");
        LoanRow approvedRow=new LoanRow(row.studentId(),row.policyId(),row.requested(),latest.score(),latest.level(),row.status(),row.nickname(),row.createdAt());
        disburse(classroomId,applicationId,approvedRow,policy,amount,teacherId,reason,now);
        LoanApplicationView result=new LoanApplicationView(applicationId,row.studentId(),row.nickname(),row.requested(),amount,latest.score(),latest.level(),"APPROVED",row.createdAt());publishApplication(classroomId,result,1);return result;
    }

    private void disburse(UUID classroomId,UUID applicationId,LoanRow row,LoanPolicyVersion policy,long amount,UUID teacherId,String reason,Instant now){
        LedgerAccount wallet=ledger.findByClassroomIdAndClassroomStudentIdAndAccountTypeAndStatus(classroomId,row.studentId(),LedgerAccountType.STUDENT_WALLET,LedgerAccountStatus.ACTIVE).orElseGet(()->ledger.save(LedgerAccount.studentWallet(uuids.generate(),classroomId,row.studentId(),now)));
        LedgerAccount receivable=ledger.findByClassroomIdAndSystemOwnerKeyAndAccountTypeAndStatus(classroomId,"LOAN_RECEIVABLE",LedgerAccountType.LOAN_RECEIVABLE,LedgerAccountStatus.ACTIVE).orElseGet(()->ledger.save(LedgerAccount.systemAccount(uuids.generate(),classroomId,"LOAN_RECEIVABLE",LedgerAccountType.LOAN_RECEIVABLE,now)));
        UUID group=uuids.generate(),loanId=uuids.generate(); LoanRepaymentPlan plan=LoanRepaymentPlan.create(amount,policy.getInterestRateBps(),policy.getTermUnits(),policy.getInstallmentCount(),now); long interest=plan.totalInterest(); Instant maturity=plan.maturesAt();
        transactions.execute(group,LedgerTransactionCommand.original(classroomId,"LOAN_DISBURSEMENT",LedgerActorType.TEACHER,teacherId,null,"/classrooms/{classroom_id}/loan-applications/{resource_id}/teacher-decisions",applicationId.toString(),applicationId.toString(),uuids.generate().toString(),"Approve educational loan",List.of(new LedgerTransactionPosting(receivable.getId(),-amount),new LedgerTransactionPosting(wallet.getId(),amount)),now),List.of(new LedgerDomainMutation(){public void validate(){} public void apply(){
            jdbc.update("update loan_applications set approved_amount=?,status='APPROVED',decision_reason=?,decided_by_user_id=?,decided_at=?,state_version=state_version+1,status_changed_at=?,updated_at=? where id=?",amount,reason,teacherId,now,now,now,applicationId);
            jdbc.update("insert into loans(id,classroom_id,loan_application_id,principal_amount,applied_interest_rate_bps,credit_level_at_approval,outstanding_principal,outstanding_interest,disbursement_transaction_group_id,started_at,matures_at,status,status_changed_at,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?,'ACTIVE',?,?,?)",loanId,classroomId,applicationId,amount,policy.getInterestRateBps(),row.level(),amount,interest,group,now,maturity,now,now,now);
            for(LoanRepaymentPlan.Installment installment:plan.installments()){Instant due=installment.dueAt();jdbc.update("insert into loan_installments(id,classroom_id,loan_id,installment_number,scheduled_principal,scheduled_interest,due_at,grace_until,status,status_changed_at,created_at,updated_at) values(?,?,?,?,?,?,?,?, 'SCHEDULED',?,?,?)",uuids.generate(),classroomId,loanId,installment.number(),installment.principal(),installment.interest(),due,due.plus(Duration.ofDays(policy.getGraceUnits())),now,now,now);}
        }}));
    }

    @Transactional
    public LoanPolicyView policy(String subject,UUID classroomId){authorization.authorize(subject,classroomId,ClassroomPermission.MANAGE_LOANS,null);return view(currentPolicy(classroomId));}

    @Transactional
    public LoanPolicyView updatePolicy(String subject,UUID classroomId,long maximumPrincipal,int interestRateBps,int termUnits,int installmentCount,int graceUnits,int onTimeCredit,int overdueCredit,int repaidCredit,Integer expectedPolicyVersion){
        authorization.authorize(subject,classroomId,ClassroomPermission.MANAGE_LOANS,null);
        if(maximumPrincipal<1||interestRateBps<0||interestRateBps>2_000||termUnits<7||termUnits>84||termUnits%7!=0||installmentCount<1||installmentCount>12||termUnits<installmentCount||graceUnits<0||graceUnits>7||onTimeCredit<0||onTimeCredit>100||overdueCredit>0||overdueCredit< -100||repaidCredit<0||repaidCredit>100)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_LOAN_POLICY","대출 한도, 금리, 주 단위 기간, 회차, 유예 및 신용점수를 다시 확인해 주세요.");
        LoanPolicyVersion active=currentPolicy(classroomId); LoanPolicyVersion locked=policies.findForUpdate(classroomId,active.getId()).orElseThrow(); if(expectedPolicyVersion!=null&&expectedPolicyVersion!=locked.getVersion())throw error(HttpStatus.CONFLICT,"POLICY_VERSION_CONFLICT","다른 화면에서 대출 정책이 변경되었습니다. 새로고침 후 다시 저장해 주세요."); Instant now=clock.instant(); int nextVersion=locked.getVersion()+1; locked.supersede(now); policies.save(locked);
        LoanPolicyVersion created=LoanPolicyVersion.activate(uuids.generate(),classroomId,nextVersion,maximumPrincipal,interestRateBps,termUnits,installmentCount,graceUnits,Map.of("on_time",onTimeCredit,"overdue",overdueCredit,"repaid",repaidCredit),now); return view(policies.save(created));
    }

    private LoanPolicyVersion currentPolicy(UUID classroomId){return policies.findTopByClassroomIdAndStatusOrderByVersionDesc(classroomId,"ACTIVE").orElseGet(()->policies.save(LoanPolicyVersion.activate(uuids.generate(),classroomId,1,300,initialInterestRateBps(classroomId),28,4,3,Map.of("on_time",10,"overdue",-30,"repaid",20),clock.instant())));}

    /**
     * 첫 대출 정책의 금리는 대출 모듈을 활성화할 때 교사가 확정한 LOAN_RATE 정책을 그대로 쓴다.
     * 이 값을 무시하고 고정값을 쓰면 교사가 AI 추천으로 적용한 금리와 운영 관리 화면의 금리가 달라진다.
     */
    private int initialInterestRateBps(UUID classroomId){
        return classroomPolicies.findByClassroomIdAndPolicyAreaAndSupersededAtIsNull(classroomId,"LOAN_RATE")
            .map(ClassroomPolicyVersion::getPolicyValues)
            .map(values->values.get("rate_bps"))
            .filter(Number.class::isInstance)
            .map(rate->((Number)rate).intValue())
            .filter(rate->rate>=0&&rate<=2_000)
            .orElse(700);
    }
    private static LoanPolicyView view(LoanPolicyVersion policy){Map<String,Object> rules=policy.getCreditAdjustments();return new LoanPolicyView(policy.getVersion(),policy.getMaximumPrincipal(),policy.getInterestRateBps(),policy.getTermUnits(),policy.getInstallmentCount(),policy.getGraceUnits(),number(rules.get("on_time"),10),number(rules.get("overdue"),-30),number(rules.get("repaid"),20));}
    private static int number(Object value,int fallback){return value instanceof Number number?number.intValue():fallback;}
    private Credit credit(UUID c,UUID s){return jdbc.query("select score,level from credit_profiles where classroom_id=? and classroom_student_id=?",rs->{if(rs.next())return new Credit(rs.getInt(1),rs.getString(2));Instant n=clock.instant();jdbc.update("insert into credit_profiles(classroom_student_id,classroom_id,level,score,evaluated_at,created_at,updated_at) values(?,?,'NORMAL',600,?,?,?)",s,c,n,n,n);return new Credit(600,"NORMAL");},c,s);}
    private static LoanApplicationView application(ResultSet rs)throws SQLException{return new LoanApplicationView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getLong(4),(Long)rs.getObject(5),rs.getInt(6),rs.getString(7),rs.getString(8),rs.getTimestamp(9).toInstant());}
    private void publishApplication(UUID classroomId,LoanApplicationView value,long stateVersion){Map<String,Object> payload=Map.of("application_id",value.applicationId().toString(),"status",value.status());events.publish(classroomId,"STUDENT_PRIVATE",value.studentId(),"loan.application.status_changed","LOAN_APPLICATION",value.applicationId(),stateVersion,payload,false);events.publish(classroomId,"TEACHER_CONTROL",null,"loan.application.status_changed","LOAN_APPLICATION",value.applicationId(),stateVersion,payload,false);}
    private static UUID student(String s){try{return UUID.fromString(s);}catch(Exception e){throw error(HttpStatus.UNAUTHORIZED,"AUTH_REQUIRED","학생 로그인이 필요합니다.");}}
    private static ClassroomOperationException error(HttpStatus s,String c,String m){return new ClassroomOperationException(s,c,m);}
    private record Credit(int score,String level){} private record Pending(UUID id,String status){} private record LoanRow(UUID studentId,UUID policyId,long requested,int score,String level,String status,String nickname,Instant createdAt){}
    public record LoanApplicationView(UUID applicationId,UUID studentId,String studentNickname,long requestedAmount,Long approvedAmount,int creditScore,String creditLevel,String status,Instant createdAt){}
    public record LoanView(UUID loanId,long principalAmount,long outstandingPrincipal,long outstandingInterest,String status,Instant maturesAt){}
    public record LoanOverviewView(int creditScore,String creditLevel,long availableLimit,int interestRateBps,int termUnits,int installmentCount,boolean canApply,UUID pendingApplicationId,String pendingApplicationStatus,List<LoanView> loans){}
    public record LoanPolicyView(int version,long maximumPrincipal,int interestRateBps,int termUnits,int installmentCount,int graceUnits,int onTimeCredit,int overdueCredit,int repaidCredit){}
}
