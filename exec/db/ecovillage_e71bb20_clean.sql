--
-- PostgreSQL database dump
--

\restrict DVy72K4IfepDaSMAgfIZ6Eexj13EnPRJcH95rwQRym0mMGnGaVdXe5RN7EsCihG

-- Dumped from database version 17.10
-- Dumped by pg_dump version 17.10

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

ALTER TABLE IF EXISTS ONLY public.votes DROP CONSTRAINT IF EXISTS fk_votes_activity;
ALTER TABLE IF EXISTS ONLY public.vote_options DROP CONSTRAINT IF EXISTS fk_vote_options_vote;
ALTER TABLE IF EXISTS ONLY public.vote_eligibilities DROP CONSTRAINT IF EXISTS fk_vote_eligibilities_vote;
ALTER TABLE IF EXISTS ONLY public.vote_eligibilities DROP CONSTRAINT IF EXISTS fk_vote_eligibilities_student;
ALTER TABLE IF EXISTS ONLY public.vote_ballots DROP CONSTRAINT IF EXISTS fk_vote_ballots_vote_option;
ALTER TABLE IF EXISTS ONLY public.vote_ballots DROP CONSTRAINT IF EXISTS fk_vote_ballots_vote;
ALTER TABLE IF EXISTS ONLY public.vote_ballots DROP CONSTRAINT IF EXISTS fk_vote_ballots_option;
ALTER TABLE IF EXISTS ONLY public.vote_ballots DROP CONSTRAINT IF EXISTS fk_vote_ballots_eligibility;
ALTER TABLE IF EXISTS ONLY public.transaction_inquiries DROP CONSTRAINT IF EXISTS fk_transaction_inquiries_student;
ALTER TABLE IF EXISTS ONLY public.transaction_inquiries DROP CONSTRAINT IF EXISTS fk_transaction_inquiries_resolver;
ALTER TABLE IF EXISTS ONLY public.transaction_inquiries DROP CONSTRAINT IF EXISTS fk_transaction_inquiries_group;
ALTER TABLE IF EXISTS ONLY public.transaction_groups DROP CONSTRAINT IF EXISTS fk_transaction_groups_root;
ALTER TABLE IF EXISTS ONLY public.transaction_groups DROP CONSTRAINT IF EXISTS fk_transaction_groups_recovery;
ALTER TABLE IF EXISTS ONLY public.transaction_groups DROP CONSTRAINT IF EXISTS fk_transaction_groups_policy_snapshot;
ALTER TABLE IF EXISTS ONLY public.transaction_groups DROP CONSTRAINT IF EXISTS fk_transaction_groups_classroom;
ALTER TABLE IF EXISTS ONLY public.transaction_business_refs DROP CONSTRAINT IF EXISTS fk_transaction_business_refs_group;
ALTER TABLE IF EXISTS ONLY public.trades DROP CONSTRAINT IF EXISTS fk_trades_transaction_group;
ALTER TABLE IF EXISTS ONLY public.trades DROP CONSTRAINT IF EXISTS fk_trades_proposer;
ALTER TABLE IF EXISTS ONLY public.trades DROP CONSTRAINT IF EXISTS fk_trades_policy_snapshot;
ALTER TABLE IF EXISTS ONLY public.trades DROP CONSTRAINT IF EXISTS fk_trades_counterpart;
ALTER TABLE IF EXISTS ONLY public.trades DROP CONSTRAINT IF EXISTS fk_trades_classroom;
ALTER TABLE IF EXISTS ONLY public.trade_money_legs DROP CONSTRAINT IF EXISTS fk_trade_money_legs_trade;
ALTER TABLE IF EXISTS ONLY public.trade_money_legs DROP CONSTRAINT IF EXISTS fk_trade_money_legs_to_student;
ALTER TABLE IF EXISTS ONLY public.trade_money_legs DROP CONSTRAINT IF EXISTS fk_trade_money_legs_from_student;
ALTER TABLE IF EXISTS ONLY public.trade_item_legs DROP CONSTRAINT IF EXISTS fk_trade_item_legs_trade;
ALTER TABLE IF EXISTS ONLY public.trade_item_legs DROP CONSTRAINT IF EXISTS fk_trade_item_legs_to_student;
ALTER TABLE IF EXISTS ONLY public.trade_item_legs DROP CONSTRAINT IF EXISTS fk_trade_item_legs_inventory_item;
ALTER TABLE IF EXISTS ONLY public.trade_item_legs DROP CONSTRAINT IF EXISTS fk_trade_item_legs_from_student;
ALTER TABLE IF EXISTS ONLY public.trade_decisions DROP CONSTRAINT IF EXISTS fk_trade_decisions_trade;
ALTER TABLE IF EXISTS ONLY public.teacher_credentials DROP CONSTRAINT IF EXISTS fk_teacher_credentials_user;
ALTER TABLE IF EXISTS ONLY public.subscription_offers DROP CONSTRAINT IF EXISTS fk_subscription_offers_drawer;
ALTER TABLE IF EXISTS ONLY public.subscription_offers DROP CONSTRAINT IF EXISTS fk_subscription_offers_classroom;
ALTER TABLE IF EXISTS ONLY public.subscription_offer_properties DROP CONSTRAINT IF EXISTS fk_subscription_offer_properties_property;
ALTER TABLE IF EXISTS ONLY public.subscription_offer_properties DROP CONSTRAINT IF EXISTS fk_subscription_offer_properties_offer;
ALTER TABLE IF EXISTS ONLY public.subscription_auto_deposit_policies DROP CONSTRAINT IF EXISTS fk_subscription_auto_deposit_policies_classroom;
ALTER TABLE IF EXISTS ONLY public.subscription_applications DROP CONSTRAINT IF EXISTS fk_subscription_applications_student;
ALTER TABLE IF EXISTS ONLY public.subscription_applications DROP CONSTRAINT IF EXISTS fk_subscription_applications_property;
ALTER TABLE IF EXISTS ONLY public.subscription_applications DROP CONSTRAINT IF EXISTS fk_subscription_applications_offer;
ALTER TABLE IF EXISTS ONLY public.subscription_accounts DROP CONSTRAINT IF EXISTS fk_subscription_accounts_student;
ALTER TABLE IF EXISTS ONLY public.subscription_account_deposits DROP CONSTRAINT IF EXISTS fk_subscription_account_deposits_group;
ALTER TABLE IF EXISTS ONLY public.subscription_account_deposits DROP CONSTRAINT IF EXISTS fk_subscription_account_deposits_account;
ALTER TABLE IF EXISTS ONLY public.student_sessions DROP CONSTRAINT IF EXISTS fk_student_sessions_student;
ALTER TABLE IF EXISTS ONLY public.student_sessions DROP CONSTRAINT IF EXISTS fk_student_sessions_device;
ALTER TABLE IF EXISTS ONLY public.student_sessions DROP CONSTRAINT IF EXISTS fk_student_sessions_classroom;
ALTER TABLE IF EXISTS ONLY public.student_credentials DROP CONSTRAINT IF EXISTS fk_student_credentials_student;
ALTER TABLE IF EXISTS ONLY public.student_character_profiles DROP CONSTRAINT IF EXISTS fk_student_character_profiles_student;
ALTER TABLE IF EXISTS ONLY public.store_stocks DROP CONSTRAINT IF EXISTS fk_store_stocks_product;
ALTER TABLE IF EXISTS ONLY public.store_products DROP CONSTRAINT IF EXISTS fk_store_products_creator;
ALTER TABLE IF EXISTS ONLY public.store_products DROP CONSTRAINT IF EXISTS fk_store_products_classroom;
ALTER TABLE IF EXISTS ONLY public.store_product_versions DROP CONSTRAINT IF EXISTS fk_store_product_versions_product;
ALTER TABLE IF EXISTS ONLY public.store_orders DROP CONSTRAINT IF EXISTS fk_store_orders_transaction_group;
ALTER TABLE IF EXISTS ONLY public.store_orders DROP CONSTRAINT IF EXISTS fk_store_orders_student;
ALTER TABLE IF EXISTS ONLY public.store_orders DROP CONSTRAINT IF EXISTS fk_store_orders_policy_snapshot;
ALTER TABLE IF EXISTS ONLY public.store_orders DROP CONSTRAINT IF EXISTS fk_store_orders_classroom;
ALTER TABLE IF EXISTS ONLY public.store_order_items DROP CONSTRAINT IF EXISTS fk_store_order_items_product_version;
ALTER TABLE IF EXISTS ONLY public.store_order_items DROP CONSTRAINT IF EXISTS fk_store_order_items_order;
ALTER TABLE IF EXISTS ONLY public.state_transitions DROP CONSTRAINT IF EXISTS fk_state_transitions_group;
ALTER TABLE IF EXISTS ONLY public.state_transitions DROP CONSTRAINT IF EXISTS fk_state_transitions_classroom;
ALTER TABLE IF EXISTS ONLY public.school_memberships DROP CONSTRAINT IF EXISTS fk_school_memberships_user;
ALTER TABLE IF EXISTS ONLY public.school_memberships DROP CONSTRAINT IF EXISTS fk_school_memberships_school;
ALTER TABLE IF EXISTS ONLY public.school_ai_setting_versions DROP CONSTRAINT IF EXISTS fk_school_ai_setting_versions_school;
ALTER TABLE IF EXISTS ONLY public.school_ai_setting_versions DROP CONSTRAINT IF EXISTS fk_school_ai_setting_versions_creator;
ALTER TABLE IF EXISTS ONLY public.savings_installments DROP CONSTRAINT IF EXISTS fk_savings_installments_group;
ALTER TABLE IF EXISTS ONLY public.savings_installments DROP CONSTRAINT IF EXISTS fk_savings_installments_account;
ALTER TABLE IF EXISTS ONLY public.savings_accounts DROP CONSTRAINT IF EXISTS fk_savings_accounts_student;
ALTER TABLE IF EXISTS ONLY public.savings_accounts DROP CONSTRAINT IF EXISTS fk_savings_accounts_product;
ALTER TABLE IF EXISTS ONLY public.savings_accounts DROP CONSTRAINT IF EXISTS fk_savings_accounts_opening_group;
ALTER TABLE IF EXISTS ONLY public.savings_accounts DROP CONSTRAINT IF EXISTS fk_savings_accounts_closing_group;
ALTER TABLE IF EXISTS ONLY public.savings_accounts DROP CONSTRAINT IF EXISTS fk_savings_accounts_classroom;
ALTER TABLE IF EXISTS ONLY public.salary_roles DROP CONSTRAINT IF EXISTS fk_salary_roles_classroom;
ALTER TABLE IF EXISTS ONLY public.report_snapshots DROP CONSTRAINT IF EXISTS fk_report_snapshots_student;
ALTER TABLE IF EXISTS ONLY public.report_snapshots DROP CONSTRAINT IF EXISTS fk_report_snapshots_classroom;
ALTER TABLE IF EXISTS ONLY public.regrading_runs DROP CONSTRAINT IF EXISTS fk_regrading_runs_starter;
ALTER TABLE IF EXISTS ONLY public.regrading_runs DROP CONSTRAINT IF EXISTS fk_regrading_runs_source_result;
ALTER TABLE IF EXISTS ONLY public.regrading_runs DROP CONSTRAINT IF EXISTS fk_regrading_runs_session;
ALTER TABLE IF EXISTS ONLY public.regrading_runs DROP CONSTRAINT IF EXISTS fk_regrading_runs_new_result;
ALTER TABLE IF EXISTS ONLY public.realtime_streams DROP CONSTRAINT IF EXISTS fk_realtime_streams_classroom;
ALTER TABLE IF EXISTS ONLY public.quiz_submissions DROP CONSTRAINT IF EXISTS fk_quiz_submissions_student;
ALTER TABLE IF EXISTS ONLY public.quiz_submissions DROP CONSTRAINT IF EXISTS fk_quiz_submissions_session;
ALTER TABLE IF EXISTS ONLY public.quiz_student_results DROP CONSTRAINT IF EXISTS fk_quiz_student_results_student;
ALTER TABLE IF EXISTS ONLY public.quiz_student_results DROP CONSTRAINT IF EXISTS fk_quiz_student_results_result_version;
ALTER TABLE IF EXISTS ONLY public.quiz_sessions DROP CONSTRAINT IF EXISTS fk_quiz_sessions_activity;
ALTER TABLE IF EXISTS ONLY public.quiz_session_questions DROP CONSTRAINT IF EXISTS fk_quiz_session_questions_session;
ALTER TABLE IF EXISTS ONLY public.quiz_session_questions DROP CONSTRAINT IF EXISTS fk_quiz_session_questions_question_version;
ALTER TABLE IF EXISTS ONLY public.quiz_result_versions DROP CONSTRAINT IF EXISTS fk_quiz_result_versions_supersedes;
ALTER TABLE IF EXISTS ONLY public.quiz_result_versions DROP CONSTRAINT IF EXISTS fk_quiz_result_versions_session;
ALTER TABLE IF EXISTS ONLY public.quiz_result_versions DROP CONSTRAINT IF EXISTS fk_quiz_result_versions_reward_group;
ALTER TABLE IF EXISTS ONLY public.quiz_questions DROP CONSTRAINT IF EXISTS fk_quiz_questions_creator;
ALTER TABLE IF EXISTS ONLY public.quiz_questions DROP CONSTRAINT IF EXISTS fk_quiz_questions_classroom;
ALTER TABLE IF EXISTS ONLY public.quiz_question_versions DROP CONSTRAINT IF EXISTS fk_quiz_question_versions_question;
ALTER TABLE IF EXISTS ONLY public.quiz_question_versions DROP CONSTRAINT IF EXISTS fk_quiz_question_versions_ai_run;
ALTER TABLE IF EXISTS ONLY public.quiz_question_issues DROP CONSTRAINT IF EXISTS fk_quiz_question_issues_session_question;
ALTER TABLE IF EXISTS ONLY public.quiz_question_issues DROP CONSTRAINT IF EXISTS fk_quiz_question_issues_resolver;
ALTER TABLE IF EXISTS ONLY public.quiz_question_issues DROP CONSTRAINT IF EXISTS fk_quiz_question_issues_reporter;
ALTER TABLE IF EXISTS ONLY public.quiz_options DROP CONSTRAINT IF EXISTS fk_quiz_options_question_version;
ALTER TABLE IF EXISTS ONLY public.quiz_answers DROP CONSTRAINT IF EXISTS fk_quiz_answers_submission;
ALTER TABLE IF EXISTS ONLY public.quiz_answers DROP CONSTRAINT IF EXISTS fk_quiz_answers_session_question;
ALTER TABLE IF EXISTS ONLY public.property_sale_listings DROP CONSTRAINT IF EXISTS fk_property_sale_listings_seller;
ALTER TABLE IF EXISTS ONLY public.property_sale_listings DROP CONSTRAINT IF EXISTS fk_property_sale_listings_property;
ALTER TABLE IF EXISTS ONLY public.property_lease_listings DROP CONSTRAINT IF EXISTS fk_property_lease_listings_property;
ALTER TABLE IF EXISTS ONLY public.property_lease_listings DROP CONSTRAINT IF EXISTS fk_property_lease_listings_landlord;
ALTER TABLE IF EXISTS ONLY public.property_layouts DROP CONSTRAINT IF EXISTS fk_property_layout_classroom;
ALTER TABLE IF EXISTS ONLY public.property_contracts DROP CONSTRAINT IF EXISTS fk_property_contracts_property;
ALTER TABLE IF EXISTS ONLY public.property_contracts DROP CONSTRAINT IF EXISTS fk_property_contracts_offer;
ALTER TABLE IF EXISTS ONLY public.property_contracts DROP CONSTRAINT IF EXISTS fk_property_contracts_occupant;
ALTER TABLE IF EXISTS ONLY public.property_contracts DROP CONSTRAINT IF EXISTS fk_property_contracts_listing;
ALTER TABLE IF EXISTS ONLY public.property_contracts DROP CONSTRAINT IF EXISTS fk_property_contracts_landlord;
ALTER TABLE IF EXISTS ONLY public.property_contracts DROP CONSTRAINT IF EXISTS fk_property_contracts_approver;
ALTER TABLE IF EXISTS ONLY public.property_contract_payments DROP CONSTRAINT IF EXISTS fk_property_contract_payments_group;
ALTER TABLE IF EXISTS ONLY public.property_contract_payments DROP CONSTRAINT IF EXISTS fk_property_contract_payments_contract;
ALTER TABLE IF EXISTS ONLY public.properties DROP CONSTRAINT IF EXISTS fk_properties_subscription_offer;
ALTER TABLE IF EXISTS ONLY public.properties DROP CONSTRAINT IF EXISTS fk_properties_owner_student;
ALTER TABLE IF EXISTS ONLY public.properties DROP CONSTRAINT IF EXISTS fk_properties_classroom;
ALTER TABLE IF EXISTS ONLY public.policy_snapshots DROP CONSTRAINT IF EXISTS fk_policy_snapshots_classroom;
ALTER TABLE IF EXISTS ONLY public.policy_snapshot_sources DROP CONSTRAINT IF EXISTS fk_policy_snapshot_sources_snapshot;
ALTER TABLE IF EXISTS ONLY public.payslips DROP CONSTRAINT IF EXISTS fk_payslips_transaction_group;
ALTER TABLE IF EXISTS ONLY public.payslips DROP CONSTRAINT IF EXISTS fk_payslips_student;
ALTER TABLE IF EXISTS ONLY public.payslips DROP CONSTRAINT IF EXISTS fk_payslips_salary_role;
ALTER TABLE IF EXISTS ONLY public.payslips DROP CONSTRAINT IF EXISTS fk_payslips_policy_snapshot;
ALTER TABLE IF EXISTS ONLY public.outbox_events DROP CONSTRAINT IF EXISTS fk_outbox_events_stream;
ALTER TABLE IF EXISTS ONLY public.loans DROP CONSTRAINT IF EXISTS fk_loans_disbursement_group;
ALTER TABLE IF EXISTS ONLY public.loans DROP CONSTRAINT IF EXISTS fk_loans_classroom;
ALTER TABLE IF EXISTS ONLY public.loans DROP CONSTRAINT IF EXISTS fk_loans_application;
ALTER TABLE IF EXISTS ONLY public.loan_policy_versions DROP CONSTRAINT IF EXISTS fk_loan_policy_versions_classroom;
ALTER TABLE IF EXISTS ONLY public.loan_installments DROP CONSTRAINT IF EXISTS fk_loan_installments_loan;
ALTER TABLE IF EXISTS ONLY public.loan_installments DROP CONSTRAINT IF EXISTS fk_loan_installments_group;
ALTER TABLE IF EXISTS ONLY public.loan_applications DROP CONSTRAINT IF EXISTS fk_loan_applications_student;
ALTER TABLE IF EXISTS ONLY public.loan_applications DROP CONSTRAINT IF EXISTS fk_loan_applications_policy;
ALTER TABLE IF EXISTS ONLY public.loan_applications DROP CONSTRAINT IF EXISTS fk_loan_applications_decider;
ALTER TABLE IF EXISTS ONLY public.loan_applications DROP CONSTRAINT IF EXISTS fk_loan_applications_classroom;
ALTER TABLE IF EXISTS ONLY public.ledger_entries DROP CONSTRAINT IF EXISTS fk_ledger_entries_group;
ALTER TABLE IF EXISTS ONLY public.ledger_entries DROP CONSTRAINT IF EXISTS fk_ledger_entries_account;
ALTER TABLE IF EXISTS ONLY public.ledger_accounts DROP CONSTRAINT IF EXISTS fk_ledger_accounts_student;
ALTER TABLE IF EXISTS ONLY public.ledger_accounts DROP CONSTRAINT IF EXISTS fk_ledger_accounts_classroom;
ALTER TABLE IF EXISTS ONLY public.investment_scenario_versions DROP CONSTRAINT IF EXISTS fk_investment_scenario_versions_market;
ALTER TABLE IF EXISTS ONLY public.investment_price_steps DROP CONSTRAINT IF EXISTS fk_investment_price_steps_asset;
ALTER TABLE IF EXISTS ONLY public.investment_positions DROP CONSTRAINT IF EXISTS fk_investment_positions_student;
ALTER TABLE IF EXISTS ONLY public.investment_positions DROP CONSTRAINT IF EXISTS fk_investment_positions_classroom;
ALTER TABLE IF EXISTS ONLY public.investment_positions DROP CONSTRAINT IF EXISTS fk_investment_positions_asset;
ALTER TABLE IF EXISTS ONLY public.investment_orders DROP CONSTRAINT IF EXISTS fk_investment_orders_student;
ALTER TABLE IF EXISTS ONLY public.investment_orders DROP CONSTRAINT IF EXISTS fk_investment_orders_group;
ALTER TABLE IF EXISTS ONLY public.investment_orders DROP CONSTRAINT IF EXISTS fk_investment_orders_classroom;
ALTER TABLE IF EXISTS ONLY public.investment_orders DROP CONSTRAINT IF EXISTS fk_investment_orders_asset;
ALTER TABLE IF EXISTS ONLY public.investment_markets DROP CONSTRAINT IF EXISTS fk_investment_markets_classroom;
ALTER TABLE IF EXISTS ONLY public.investment_markets DROP CONSTRAINT IF EXISTS fk_investment_markets_active_scenario;
ALTER TABLE IF EXISTS ONLY public.investment_assets DROP CONSTRAINT IF EXISTS fk_investment_assets_scenario;
ALTER TABLE IF EXISTS ONLY public.investment_assets DROP CONSTRAINT IF EXISTS fk_investment_assets_classroom;
ALTER TABLE IF EXISTS ONLY public.inventory_items DROP CONSTRAINT IF EXISTS fk_inventory_items_student;
ALTER TABLE IF EXISTS ONLY public.inventory_items DROP CONSTRAINT IF EXISTS fk_inventory_items_source_order_item;
ALTER TABLE IF EXISTS ONLY public.inventory_items DROP CONSTRAINT IF EXISTS fk_inventory_items_locked_trade;
ALTER TABLE IF EXISTS ONLY public.inventory_items DROP CONSTRAINT IF EXISTS fk_inventory_items_classroom;
ALTER TABLE IF EXISTS ONLY public.inventory_items DROP CONSTRAINT IF EXISTS fk_inventory_items_catalog;
ALTER TABLE IF EXISTS ONLY public.idempotency_records DROP CONSTRAINT IF EXISTS fk_idempotency_records_group;
ALTER TABLE IF EXISTS ONLY public.idempotency_records DROP CONSTRAINT IF EXISTS fk_idempotency_records_classroom;
ALTER TABLE IF EXISTS ONLY public.financial_products DROP CONSTRAINT IF EXISTS fk_financial_products_classroom;
ALTER TABLE IF EXISTS ONLY public.financial_product_versions DROP CONSTRAINT IF EXISTS fk_financial_product_versions_product;
ALTER TABLE IF EXISTS ONLY public.export_operations DROP CONSTRAINT IF EXISTS fk_export_operations_requester;
ALTER TABLE IF EXISTS ONLY public.export_operations DROP CONSTRAINT IF EXISTS fk_export_operations_async_operation;
ALTER TABLE IF EXISTS ONLY public.economic_event_drafts DROP CONSTRAINT IF EXISTS fk_economic_event_drafts_source_proposal;
ALTER TABLE IF EXISTS ONLY public.economic_event_drafts DROP CONSTRAINT IF EXISTS fk_economic_event_drafts_classroom;
ALTER TABLE IF EXISTS ONLY public.economic_event_drafts DROP CONSTRAINT IF EXISTS fk_economic_event_drafts_ai_run;
ALTER TABLE IF EXISTS ONLY public.economic_event_drafts DROP CONSTRAINT IF EXISTS fk_economic_event_drafts_activity;
ALTER TABLE IF EXISTS ONLY public.device_links DROP CONSTRAINT IF EXISTS fk_device_links_student;
ALTER TABLE IF EXISTS ONLY public.device_links DROP CONSTRAINT IF EXISTS fk_device_links_classroom;
ALTER TABLE IF EXISTS ONLY public.deposit_accounts DROP CONSTRAINT IF EXISTS fk_deposit_accounts_student;
ALTER TABLE IF EXISTS ONLY public.deposit_accounts DROP CONSTRAINT IF EXISTS fk_deposit_accounts_product;
ALTER TABLE IF EXISTS ONLY public.deposit_accounts DROP CONSTRAINT IF EXISTS fk_deposit_accounts_opening_group;
ALTER TABLE IF EXISTS ONLY public.deposit_accounts DROP CONSTRAINT IF EXISTS fk_deposit_accounts_closing_group;
ALTER TABLE IF EXISTS ONLY public.deposit_accounts DROP CONSTRAINT IF EXISTS fk_deposit_accounts_classroom;
ALTER TABLE IF EXISTS ONLY public.credit_profiles DROP CONSTRAINT IF EXISTS fk_credit_profiles_student;
ALTER TABLE IF EXISTS ONLY public.credit_events DROP CONSTRAINT IF EXISTS fk_credit_events_profile;
ALTER TABLE IF EXISTS ONLY public.course_template_versions DROP CONSTRAINT IF EXISTS fk_course_template_versions_template;
ALTER TABLE IF EXISTS ONLY public.course_template_modules DROP CONSTRAINT IF EXISTS fk_course_template_modules_version;
ALTER TABLE IF EXISTS ONLY public.classrooms DROP CONSTRAINT IF EXISTS fk_classrooms_school;
ALTER TABLE IF EXISTS ONLY public.classrooms DROP CONSTRAINT IF EXISTS fk_classrooms_course_version;
ALTER TABLE IF EXISTS ONLY public.classroom_teachers DROP CONSTRAINT IF EXISTS fk_classroom_teachers_user;
ALTER TABLE IF EXISTS ONLY public.classroom_teachers DROP CONSTRAINT IF EXISTS fk_classroom_teachers_classroom;
ALTER TABLE IF EXISTS ONLY public.classroom_students DROP CONSTRAINT IF EXISTS fk_classroom_students_salary_role;
ALTER TABLE IF EXISTS ONLY public.classroom_students DROP CONSTRAINT IF EXISTS fk_classroom_students_classroom;
ALTER TABLE IF EXISTS ONLY public.classroom_policy_versions DROP CONSTRAINT IF EXISTS fk_classroom_policy_versions_classroom;
ALTER TABLE IF EXISTS ONLY public.classroom_modules DROP CONSTRAINT IF EXISTS fk_classroom_modules_classroom;
ALTER TABLE IF EXISTS ONLY public.classroom_characters DROP CONSTRAINT IF EXISTS fk_classroom_characters_product;
ALTER TABLE IF EXISTS ONLY public.classroom_characters DROP CONSTRAINT IF EXISTS fk_classroom_characters_classroom;
ALTER TABLE IF EXISTS ONLY public.classroom_characters DROP CONSTRAINT IF EXISTS fk_classroom_characters_catalog;
ALTER TABLE IF EXISTS ONLY public.character_equipment DROP CONSTRAINT IF EXISTS fk_character_equipment_profile;
ALTER TABLE IF EXISTS ONLY public.character_equipment DROP CONSTRAINT IF EXISTS fk_character_equipment_owned_item;
ALTER TABLE IF EXISTS ONLY public.autonomous_schedules DROP CONSTRAINT IF EXISTS fk_autonomous_schedules_classroom;
ALTER TABLE IF EXISTS ONLY public.autonomous_schedule_modules DROP CONSTRAINT IF EXISTS fk_autonomous_schedule_modules_schedule;
ALTER TABLE IF EXISTS ONLY public.autonomous_schedule_modules DROP CONSTRAINT IF EXISTS fk_autonomous_schedule_modules_classroom_module;
ALTER TABLE IF EXISTS ONLY public.audit_events DROP CONSTRAINT IF EXISTS fk_audit_events_group;
ALTER TABLE IF EXISTS ONLY public.audit_events DROP CONSTRAINT IF EXISTS fk_audit_events_classroom;
ALTER TABLE IF EXISTS ONLY public.async_operations DROP CONSTRAINT IF EXISTS fk_async_operations_requester;
ALTER TABLE IF EXISTS ONLY public.async_operations DROP CONSTRAINT IF EXISTS fk_async_operations_classroom;
ALTER TABLE IF EXISTS ONLY public.ai_runs DROP CONSTRAINT IF EXISTS fk_ai_runs_requester;
ALTER TABLE IF EXISTS ONLY public.ai_runs DROP CONSTRAINT IF EXISTS fk_ai_runs_classroom;
ALTER TABLE IF EXISTS ONLY public.ai_runs DROP CONSTRAINT IF EXISTS fk_ai_runs_async_operation;
ALTER TABLE IF EXISTS ONLY public.ai_proposals DROP CONSTRAINT IF EXISTS fk_ai_proposals_run;
ALTER TABLE IF EXISTS ONLY public.ai_proposals DROP CONSTRAINT IF EXISTS fk_ai_proposals_policy_version;
ALTER TABLE IF EXISTS ONLY public.ai_decisions DROP CONSTRAINT IF EXISTS fk_ai_decisions_proposal;
ALTER TABLE IF EXISTS ONLY public.ai_decisions DROP CONSTRAINT IF EXISTS fk_ai_decisions_decider;
ALTER TABLE IF EXISTS ONLY public.ai_artifacts DROP CONSTRAINT IF EXISTS fk_ai_artifacts_run;
ALTER TABLE IF EXISTS ONLY public.activity_sessions DROP CONSTRAINT IF EXISTS fk_activity_sessions_policy_snapshot;
ALTER TABLE IF EXISTS ONLY public.activity_sessions DROP CONSTRAINT IF EXISTS fk_activity_sessions_creator;
ALTER TABLE IF EXISTS ONLY public.activity_sessions DROP CONSTRAINT IF EXISTS fk_activity_sessions_classroom;
ALTER TABLE IF EXISTS ONLY public.activity_participants DROP CONSTRAINT IF EXISTS fk_activity_participants_student;
ALTER TABLE IF EXISTS ONLY public.activity_participants DROP CONSTRAINT IF EXISTS fk_activity_participants_session;
ALTER TABLE IF EXISTS ONLY public.activity_presentation_slides DROP CONSTRAINT IF EXISTS activity_presentation_slides_activity_session_id_fkey;
DROP TRIGGER IF EXISTS trg_vote_ballots_immutable ON public.vote_ballots;
DROP TRIGGER IF EXISTS trg_transaction_groups_normalize_recovery ON public.transaction_groups;
DROP TRIGGER IF EXISTS trg_transaction_groups_immutable ON public.transaction_groups;
DROP TRIGGER IF EXISTS trg_transaction_groups_committed_balance ON public.transaction_groups;
DROP TRIGGER IF EXISTS trg_trades_item_lock_consistency ON public.trades;
DROP TRIGGER IF EXISTS trg_trade_item_legs_single_active_trade ON public.trade_item_legs;
DROP TRIGGER IF EXISTS trg_store_product_versions_published ON public.store_product_versions;
DROP TRIGGER IF EXISTS trg_state_transitions_immutable ON public.state_transitions;
DROP TRIGGER IF EXISTS trg_report_snapshots_immutable ON public.report_snapshots;
DROP TRIGGER IF EXISTS trg_quiz_student_results_published ON public.quiz_student_results;
DROP TRIGGER IF EXISTS trg_quiz_session_questions_immutable ON public.quiz_session_questions;
DROP TRIGGER IF EXISTS trg_quiz_result_versions_published ON public.quiz_result_versions;
DROP TRIGGER IF EXISTS trg_quiz_question_versions_published ON public.quiz_question_versions;
DROP TRIGGER IF EXISTS trg_quiz_question_versions_correct_answer ON public.quiz_question_versions;
DROP TRIGGER IF EXISTS trg_quiz_options_correct_answer ON public.quiz_options;
DROP TRIGGER IF EXISTS trg_policy_snapshots_immutable ON public.policy_snapshots;
DROP TRIGGER IF EXISTS trg_policy_snapshot_sources_immutable ON public.policy_snapshot_sources;
DROP TRIGGER IF EXISTS trg_outbox_events_payload_immutable ON public.outbox_events;
DROP TRIGGER IF EXISTS trg_loan_policy_versions_effective ON public.loan_policy_versions;
DROP TRIGGER IF EXISTS trg_ledger_entries_immutable ON public.ledger_entries;
DROP TRIGGER IF EXISTS trg_ledger_entries_committed_balance ON public.ledger_entries;
DROP TRIGGER IF EXISTS trg_investment_scenario_versions_published ON public.investment_scenario_versions;
DROP TRIGGER IF EXISTS trg_inventory_items_equipment_lock ON public.inventory_items;
DROP TRIGGER IF EXISTS trg_financial_product_versions_published ON public.financial_product_versions;
DROP TRIGGER IF EXISTS trg_credit_events_immutable ON public.credit_events;
DROP TRIGGER IF EXISTS trg_course_template_versions_published ON public.course_template_versions;
DROP TRIGGER IF EXISTS trg_classrooms_immutable_identity ON public.classrooms;
DROP TRIGGER IF EXISTS trg_classroom_teacher_seed_character_store ON public.classroom_teachers;
DROP TRIGGER IF EXISTS trg_classroom_policy_versions_effective ON public.classroom_policy_versions;
DROP TRIGGER IF EXISTS trg_character_equipment_validate_item ON public.character_equipment;
DROP TRIGGER IF EXISTS trg_audit_events_immutable ON public.audit_events;
DROP TRIGGER IF EXISTS trg_ai_artifacts_immutable ON public.ai_artifacts;
DROP INDEX IF EXISTS public.ux_student_sessions_active_student;
DROP INDEX IF EXISTS public.ux_store_products_name_kind;
DROP INDEX IF EXISTS public.ux_school_memberships_active_role;
DROP INDEX IF EXISTS public.ux_school_ai_setting_versions_current;
DROP INDEX IF EXISTS public.ux_salary_roles_role_code;
DROP INDEX IF EXISTS public.ux_salary_roles_name;
DROP INDEX IF EXISTS public.ux_realtime_streams_resource;
DROP INDEX IF EXISTS public.ux_property_sale_listing_active_property;
DROP INDEX IF EXISTS public.ux_properties_active_coordinate;
DROP INDEX IF EXISTS public.ux_ledger_accounts_active_student_wallet;
DROP INDEX IF EXISTS public.ux_ledger_accounts_active_classroom_fund;
DROP INDEX IF EXISTS public.ux_investment_markets_open;
DROP INDEX IF EXISTS public.ux_financial_products_type_name;
DROP INDEX IF EXISTS public.ux_classroom_teachers_active_role;
DROP INDEX IF EXISTS public.ux_classroom_teachers_active_owner;
DROP INDEX IF EXISTS public.ux_classroom_students_active_roster;
DROP INDEX IF EXISTS public.ux_classroom_students_active_nickname;
DROP INDEX IF EXISTS public.ux_classroom_policy_versions_current;
DROP INDEX IF EXISTS public.uq_regrading_runs_active_session;
DROP INDEX IF EXISTS public.uq_quiz_question_issues_student_report;
DROP INDEX IF EXISTS public.uq_ai_proposals_monitor_dedupe;
DROP INDEX IF EXISTS public.uq_ai_decisions_final;
DROP INDEX IF EXISTS public.uq_ai_decisions_acknowledge;
DROP INDEX IF EXISTS public.ix_vote_ballots_vote_option;
DROP INDEX IF EXISTS public.ix_transaction_inquiries_group;
DROP INDEX IF EXISTS public.ix_transaction_groups_type_status;
DROP INDEX IF EXISTS public.ix_transaction_groups_recovery;
DROP INDEX IF EXISTS public.ix_transaction_groups_classroom_committed;
DROP INDEX IF EXISTS public.ix_trades_teacher_queue;
DROP INDEX IF EXISTS public.ix_trades_proposer_status;
DROP INDEX IF EXISTS public.ix_trades_counterpart_status;
DROP INDEX IF EXISTS public.ix_trade_item_legs_inventory;
DROP INDEX IF EXISTS public.ix_subscription_offers_open;
DROP INDEX IF EXISTS public.ix_student_sessions_expires;
DROP INDEX IF EXISTS public.ix_student_sessions_device;
DROP INDEX IF EXISTS public.ix_student_character_profiles_student;
DROP INDEX IF EXISTS public.ix_store_products_classroom_status;
DROP INDEX IF EXISTS public.ix_store_orders_student_time;
DROP INDEX IF EXISTS public.ix_store_orders_fulfillment;
DROP INDEX IF EXISTS public.ix_store_order_items_order;
DROP INDEX IF EXISTS public.ix_state_transitions_aggregate;
DROP INDEX IF EXISTS public.ix_savings_installments_due;
DROP INDEX IF EXISTS public.ix_savings_installments_account;
DROP INDEX IF EXISTS public.ix_report_snapshots_retention;
DROP INDEX IF EXISTS public.ix_quiz_submissions_session_student;
DROP INDEX IF EXISTS public.ix_quiz_results_session_version;
DROP INDEX IF EXISTS public.ix_quiz_answers_submission;
DROP INDEX IF EXISTS public.ix_property_sale_listings_classroom;
DROP INDEX IF EXISTS public.ix_property_contracts_due;
DROP INDEX IF EXISTS public.ix_property_contract_payments_due;
DROP INDEX IF EXISTS public.ix_properties_current_layout;
DROP INDEX IF EXISTS public.ix_properties_available;
DROP INDEX IF EXISTS public.ix_policy_snapshot_sources_snapshot;
DROP INDEX IF EXISTS public.ix_payslips_student_issued;
DROP INDEX IF EXISTS public.ix_outbox_unpublished;
DROP INDEX IF EXISTS public.ix_loan_installments_loan;
DROP INDEX IF EXISTS public.ix_loan_installments_due;
DROP INDEX IF EXISTS public.ix_loan_applications_queue;
DROP INDEX IF EXISTS public.ix_ledger_entries_group;
DROP INDEX IF EXISTS public.ix_ledger_entries_account_sequence;
DROP INDEX IF EXISTS public.ix_investment_price_steps_reveal;
DROP INDEX IF EXISTS public.ix_investment_orders_student_time;
DROP INDEX IF EXISTS public.ix_investment_assets_scenario;
DROP INDEX IF EXISTS public.ix_inventory_items_owner_status;
DROP INDEX IF EXISTS public.ix_inventory_items_locked_trade;
DROP INDEX IF EXISTS public.ix_idempotency_expires;
DROP INDEX IF EXISTS public.ix_deposit_accounts_maturity;
DROP INDEX IF EXISTS public.ix_credit_events_student_time;
DROP INDEX IF EXISTS public.ix_classrooms_school_status;
DROP INDEX IF EXISTS public.ix_classroom_teachers_user;
DROP INDEX IF EXISTS public.ix_classroom_students_salary_role;
DROP INDEX IF EXISTS public.ix_classroom_students_classroom_status;
DROP INDEX IF EXISTS public.ix_classroom_modules_status;
DROP INDEX IF EXISTS public.ix_character_equipment_profile;
DROP INDEX IF EXISTS public.ix_character_equipment_owner;
DROP INDEX IF EXISTS public.ix_autonomous_schedules_lookup;
DROP INDEX IF EXISTS public.ix_autonomous_schedule_modules_module;
DROP INDEX IF EXISTS public.ix_audit_events_classroom_time;
DROP INDEX IF EXISTS public.ix_async_operations_status;
DROP INDEX IF EXISTS public.ix_ai_runs_classroom_status;
DROP INDEX IF EXISTS public.ix_ai_artifacts_run;
DROP INDEX IF EXISTS public.ix_activity_sessions_classroom_status;
DROP INDEX IF EXISTS public.ix_activity_presentation_slides_activity;
DROP INDEX IF EXISTS public.ix_activity_participants_session;
DROP INDEX IF EXISTS public.flyway_schema_history_s_idx;
ALTER TABLE IF EXISTS ONLY public.votes DROP CONSTRAINT IF EXISTS votes_pkey;
ALTER TABLE IF EXISTS ONLY public.vote_options DROP CONSTRAINT IF EXISTS vote_options_pkey;
ALTER TABLE IF EXISTS ONLY public.vote_eligibilities DROP CONSTRAINT IF EXISTS vote_eligibilities_pkey;
ALTER TABLE IF EXISTS ONLY public.vote_ballots DROP CONSTRAINT IF EXISTS vote_ballots_pkey;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_pkey;
ALTER TABLE IF EXISTS ONLY public.votes DROP CONSTRAINT IF EXISTS uq_votes_scope;
ALTER TABLE IF EXISTS ONLY public.vote_options DROP CONSTRAINT IF EXISTS uq_vote_options_vote_scope;
ALTER TABLE IF EXISTS ONLY public.vote_options DROP CONSTRAINT IF EXISTS uq_vote_options_sort_order;
ALTER TABLE IF EXISTS ONLY public.vote_options DROP CONSTRAINT IF EXISTS uq_vote_options_scope;
ALTER TABLE IF EXISTS ONLY public.vote_eligibilities DROP CONSTRAINT IF EXISTS uq_vote_eligibilities_vote_token_scope;
ALTER TABLE IF EXISTS ONLY public.vote_eligibilities DROP CONSTRAINT IF EXISTS uq_vote_eligibilities_token;
ALTER TABLE IF EXISTS ONLY public.vote_eligibilities DROP CONSTRAINT IF EXISTS uq_vote_eligibilities_student;
ALTER TABLE IF EXISTS ONLY public.vote_eligibilities DROP CONSTRAINT IF EXISTS uq_vote_eligibilities_scope;
ALTER TABLE IF EXISTS ONLY public.vote_ballots DROP CONSTRAINT IF EXISTS uq_vote_ballots_token;
ALTER TABLE IF EXISTS ONLY public.vote_ballots DROP CONSTRAINT IF EXISTS uq_vote_ballots_scope;
ALTER TABLE IF EXISTS ONLY public.vote_ballots DROP CONSTRAINT IF EXISTS uq_vote_ballots_receipt;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS uq_users_external_subject;
ALTER TABLE IF EXISTS ONLY public.transaction_inquiries DROP CONSTRAINT IF EXISTS uq_transaction_inquiries_scope;
ALTER TABLE IF EXISTS ONLY public.transaction_groups DROP CONSTRAINT IF EXISTS uq_transaction_groups_scope;
ALTER TABLE IF EXISTS ONLY public.transaction_business_refs DROP CONSTRAINT IF EXISTS uq_transaction_business_refs_scope;
ALTER TABLE IF EXISTS ONLY public.transaction_business_refs DROP CONSTRAINT IF EXISTS uq_transaction_business_refs_ref;
ALTER TABLE IF EXISTS ONLY public.trades DROP CONSTRAINT IF EXISTS uq_trades_scope;
ALTER TABLE IF EXISTS ONLY public.trade_money_legs DROP CONSTRAINT IF EXISTS uq_trade_money_legs_scope;
ALTER TABLE IF EXISTS ONLY public.trade_money_legs DROP CONSTRAINT IF EXISTS uq_trade_money_legs_direction;
ALTER TABLE IF EXISTS ONLY public.trade_item_legs DROP CONSTRAINT IF EXISTS uq_trade_item_legs_scope;
ALTER TABLE IF EXISTS ONLY public.trade_item_legs DROP CONSTRAINT IF EXISTS uq_trade_item_legs_item;
ALTER TABLE IF EXISTS ONLY public.trade_decisions DROP CONSTRAINT IF EXISTS uq_trade_decisions_type;
ALTER TABLE IF EXISTS ONLY public.trade_decisions DROP CONSTRAINT IF EXISTS uq_trade_decisions_scope;
ALTER TABLE IF EXISTS ONLY public.teacher_credentials DROP CONSTRAINT IF EXISTS uq_teacher_credentials_email;
ALTER TABLE IF EXISTS ONLY public.subscription_offers DROP CONSTRAINT IF EXISTS uq_subscription_offers_scope;
ALTER TABLE IF EXISTS ONLY public.subscription_applications DROP CONSTRAINT IF EXISTS uq_subscription_applications_student_offer;
ALTER TABLE IF EXISTS ONLY public.subscription_applications DROP CONSTRAINT IF EXISTS uq_subscription_applications_scope;
ALTER TABLE IF EXISTS ONLY public.subscription_account_deposits DROP CONSTRAINT IF EXISTS uq_subscription_account_deposits_scope;
ALTER TABLE IF EXISTS ONLY public.student_sessions DROP CONSTRAINT IF EXISTS uq_student_sessions_token;
ALTER TABLE IF EXISTS ONLY public.student_sessions DROP CONSTRAINT IF EXISTS uq_student_sessions_scope;
ALTER TABLE IF EXISTS ONLY public.student_credentials DROP CONSTRAINT IF EXISTS uq_student_credentials_scope;
ALTER TABLE IF EXISTS ONLY public.student_character_profiles DROP CONSTRAINT IF EXISTS uq_student_character_profiles_student;
ALTER TABLE IF EXISTS ONLY public.student_character_profiles DROP CONSTRAINT IF EXISTS uq_student_character_profiles_scope;
ALTER TABLE IF EXISTS ONLY public.student_character_profiles DROP CONSTRAINT IF EXISTS uq_student_character_profiles_owner;
ALTER TABLE IF EXISTS ONLY public.store_stocks DROP CONSTRAINT IF EXISTS uq_store_stocks_scope;
ALTER TABLE IF EXISTS ONLY public.store_products DROP CONSTRAINT IF EXISTS uq_store_products_scope;
ALTER TABLE IF EXISTS ONLY public.store_product_versions DROP CONSTRAINT IF EXISTS uq_store_product_versions_version;
ALTER TABLE IF EXISTS ONLY public.store_product_versions DROP CONSTRAINT IF EXISTS uq_store_product_versions_scope;
ALTER TABLE IF EXISTS ONLY public.store_orders DROP CONSTRAINT IF EXISTS uq_store_orders_scope;
ALTER TABLE IF EXISTS ONLY public.store_order_items DROP CONSTRAINT IF EXISTS uq_store_order_items_scope;
ALTER TABLE IF EXISTS ONLY public.store_order_items DROP CONSTRAINT IF EXISTS uq_store_order_items_product;
ALTER TABLE IF EXISTS ONLY public.state_transitions DROP CONSTRAINT IF EXISTS uq_state_transitions_version;
ALTER TABLE IF EXISTS ONLY public.state_transitions DROP CONSTRAINT IF EXISTS uq_state_transitions_scope;
ALTER TABLE IF EXISTS ONLY public.schools DROP CONSTRAINT IF EXISTS uq_schools_school_code;
ALTER TABLE IF EXISTS ONLY public.school_ai_setting_versions DROP CONSTRAINT IF EXISTS uq_school_ai_setting_versions_version;
ALTER TABLE IF EXISTS ONLY public.savings_installments DROP CONSTRAINT IF EXISTS uq_savings_installments_scope;
ALTER TABLE IF EXISTS ONLY public.savings_installments DROP CONSTRAINT IF EXISTS uq_savings_installments_number;
ALTER TABLE IF EXISTS ONLY public.savings_accounts DROP CONSTRAINT IF EXISTS uq_savings_accounts_scope;
ALTER TABLE IF EXISTS ONLY public.salary_roles DROP CONSTRAINT IF EXISTS uq_salary_roles_scope;
ALTER TABLE IF EXISTS ONLY public.report_snapshots DROP CONSTRAINT IF EXISTS uq_report_snapshots_scope;
ALTER TABLE IF EXISTS ONLY public.regrading_runs DROP CONSTRAINT IF EXISTS uq_regrading_runs_scope;
ALTER TABLE IF EXISTS ONLY public.realtime_streams DROP CONSTRAINT IF EXISTS uq_realtime_streams_scope;
ALTER TABLE IF EXISTS ONLY public.quiz_submissions DROP CONSTRAINT IF EXISTS uq_quiz_submissions_student_version;
ALTER TABLE IF EXISTS ONLY public.quiz_submissions DROP CONSTRAINT IF EXISTS uq_quiz_submissions_scope;
ALTER TABLE IF EXISTS ONLY public.quiz_student_results DROP CONSTRAINT IF EXISTS uq_quiz_student_results_student;
ALTER TABLE IF EXISTS ONLY public.quiz_student_results DROP CONSTRAINT IF EXISTS uq_quiz_student_results_scope;
ALTER TABLE IF EXISTS ONLY public.quiz_sessions DROP CONSTRAINT IF EXISTS uq_quiz_sessions_scope;
ALTER TABLE IF EXISTS ONLY public.quiz_session_questions DROP CONSTRAINT IF EXISTS uq_quiz_session_questions_sequence;
ALTER TABLE IF EXISTS ONLY public.quiz_session_questions DROP CONSTRAINT IF EXISTS uq_quiz_session_questions_scope;
ALTER TABLE IF EXISTS ONLY public.quiz_result_versions DROP CONSTRAINT IF EXISTS uq_quiz_result_versions_version;
ALTER TABLE IF EXISTS ONLY public.quiz_result_versions DROP CONSTRAINT IF EXISTS uq_quiz_result_versions_scope;
ALTER TABLE IF EXISTS ONLY public.quiz_questions DROP CONSTRAINT IF EXISTS uq_quiz_questions_scope;
ALTER TABLE IF EXISTS ONLY public.quiz_question_versions DROP CONSTRAINT IF EXISTS uq_quiz_question_versions_version;
ALTER TABLE IF EXISTS ONLY public.quiz_question_versions DROP CONSTRAINT IF EXISTS uq_quiz_question_versions_scope;
ALTER TABLE IF EXISTS ONLY public.quiz_question_issues DROP CONSTRAINT IF EXISTS uq_quiz_question_issues_scope;
ALTER TABLE IF EXISTS ONLY public.quiz_options DROP CONSTRAINT IF EXISTS uq_quiz_options_scope;
ALTER TABLE IF EXISTS ONLY public.quiz_options DROP CONSTRAINT IF EXISTS uq_quiz_options_key;
ALTER TABLE IF EXISTS ONLY public.quiz_answers DROP CONSTRAINT IF EXISTS uq_quiz_answers_scope;
ALTER TABLE IF EXISTS ONLY public.quiz_answers DROP CONSTRAINT IF EXISTS uq_quiz_answers_question;
ALTER TABLE IF EXISTS ONLY public.property_sale_listings DROP CONSTRAINT IF EXISTS uq_property_sale_listings_scope;
ALTER TABLE IF EXISTS ONLY public.property_lease_listings DROP CONSTRAINT IF EXISTS uq_property_lease_listings_scope;
ALTER TABLE IF EXISTS ONLY public.property_contracts DROP CONSTRAINT IF EXISTS uq_property_contracts_scope;
ALTER TABLE IF EXISTS ONLY public.property_contract_payments DROP CONSTRAINT IF EXISTS uq_property_contract_payments_scope;
ALTER TABLE IF EXISTS ONLY public.property_contract_payments DROP CONSTRAINT IF EXISTS uq_property_contract_payments_due;
ALTER TABLE IF EXISTS ONLY public.properties DROP CONSTRAINT IF EXISTS uq_properties_scope;
ALTER TABLE IF EXISTS ONLY public.policy_snapshots DROP CONSTRAINT IF EXISTS uq_policy_snapshots_scope;
ALTER TABLE IF EXISTS ONLY public.policy_snapshots DROP CONSTRAINT IF EXISTS uq_policy_snapshots_hash;
ALTER TABLE IF EXISTS ONLY public.policy_snapshot_sources DROP CONSTRAINT IF EXISTS uq_policy_snapshot_sources_source;
ALTER TABLE IF EXISTS ONLY public.policy_snapshot_sources DROP CONSTRAINT IF EXISTS uq_policy_snapshot_sources_scope;
ALTER TABLE IF EXISTS ONLY public.payslips DROP CONSTRAINT IF EXISTS uq_payslips_student_period;
ALTER TABLE IF EXISTS ONLY public.payslips DROP CONSTRAINT IF EXISTS uq_payslips_scope;
ALTER TABLE IF EXISTS ONLY public.outbox_events DROP CONSTRAINT IF EXISTS uq_outbox_events_sequence;
ALTER TABLE IF EXISTS ONLY public.outbox_events DROP CONSTRAINT IF EXISTS uq_outbox_events_scope;
ALTER TABLE IF EXISTS ONLY public.loans DROP CONSTRAINT IF EXISTS uq_loans_scope;
ALTER TABLE IF EXISTS ONLY public.loans DROP CONSTRAINT IF EXISTS uq_loans_application;
ALTER TABLE IF EXISTS ONLY public.loan_policy_versions DROP CONSTRAINT IF EXISTS uq_loan_policy_versions_version;
ALTER TABLE IF EXISTS ONLY public.loan_policy_versions DROP CONSTRAINT IF EXISTS uq_loan_policy_versions_scope;
ALTER TABLE IF EXISTS ONLY public.loan_installments DROP CONSTRAINT IF EXISTS uq_loan_installments_scope;
ALTER TABLE IF EXISTS ONLY public.loan_installments DROP CONSTRAINT IF EXISTS uq_loan_installments_number;
ALTER TABLE IF EXISTS ONLY public.loan_applications DROP CONSTRAINT IF EXISTS uq_loan_applications_scope;
ALTER TABLE IF EXISTS ONLY public.ledger_entries DROP CONSTRAINT IF EXISTS uq_ledger_entries_scope;
ALTER TABLE IF EXISTS ONLY public.ledger_entries DROP CONSTRAINT IF EXISTS uq_ledger_entries_group_account;
ALTER TABLE IF EXISTS ONLY public.ledger_entries DROP CONSTRAINT IF EXISTS uq_ledger_entries_account_sequence;
ALTER TABLE IF EXISTS ONLY public.ledger_accounts DROP CONSTRAINT IF EXISTS uq_ledger_accounts_scope;
ALTER TABLE IF EXISTS ONLY public.investment_scenario_versions DROP CONSTRAINT IF EXISTS uq_investment_scenario_versions_version;
ALTER TABLE IF EXISTS ONLY public.investment_scenario_versions DROP CONSTRAINT IF EXISTS uq_investment_scenario_versions_scope;
ALTER TABLE IF EXISTS ONLY public.investment_price_steps DROP CONSTRAINT IF EXISTS uq_investment_price_steps_step;
ALTER TABLE IF EXISTS ONLY public.investment_price_steps DROP CONSTRAINT IF EXISTS uq_investment_price_steps_scope;
ALTER TABLE IF EXISTS ONLY public.investment_positions DROP CONSTRAINT IF EXISTS uq_investment_positions_student_asset;
ALTER TABLE IF EXISTS ONLY public.investment_positions DROP CONSTRAINT IF EXISTS uq_investment_positions_scope;
ALTER TABLE IF EXISTS ONLY public.investment_orders DROP CONSTRAINT IF EXISTS uq_investment_orders_scope;
ALTER TABLE IF EXISTS ONLY public.investment_markets DROP CONSTRAINT IF EXISTS uq_investment_markets_scope;
ALTER TABLE IF EXISTS ONLY public.investment_markets DROP CONSTRAINT IF EXISTS uq_investment_markets_mode_scope;
ALTER TABLE IF EXISTS ONLY public.investment_assets DROP CONSTRAINT IF EXISTS uq_investment_assets_symbol;
ALTER TABLE IF EXISTS ONLY public.investment_assets DROP CONSTRAINT IF EXISTS uq_investment_assets_scope;
ALTER TABLE IF EXISTS ONLY public.inventory_items DROP CONSTRAINT IF EXISTS uq_inventory_items_scope;
ALTER TABLE IF EXISTS ONLY public.inventory_items DROP CONSTRAINT IF EXISTS uq_inventory_items_owner;
ALTER TABLE IF EXISTS ONLY public.idempotency_records DROP CONSTRAINT IF EXISTS uq_idempotency_records_scope;
ALTER TABLE IF EXISTS ONLY public.idempotency_records DROP CONSTRAINT IF EXISTS uq_idempotency_records_key;
ALTER TABLE IF EXISTS ONLY public.financial_products DROP CONSTRAINT IF EXISTS uq_financial_products_scope;
ALTER TABLE IF EXISTS ONLY public.financial_product_versions DROP CONSTRAINT IF EXISTS uq_financial_product_versions_version;
ALTER TABLE IF EXISTS ONLY public.financial_product_versions DROP CONSTRAINT IF EXISTS uq_financial_product_versions_scope;
ALTER TABLE IF EXISTS ONLY public.export_operations DROP CONSTRAINT IF EXISTS uq_export_operations_scope;
ALTER TABLE IF EXISTS ONLY public.economic_event_drafts DROP CONSTRAINT IF EXISTS uq_economic_event_drafts_scope;
ALTER TABLE IF EXISTS ONLY public.device_links DROP CONSTRAINT IF EXISTS uq_device_links_scope;
ALTER TABLE IF EXISTS ONLY public.device_links DROP CONSTRAINT IF EXISTS uq_device_links_fingerprint;
ALTER TABLE IF EXISTS ONLY public.deposit_accounts DROP CONSTRAINT IF EXISTS uq_deposit_accounts_scope;
ALTER TABLE IF EXISTS ONLY public.credit_profiles DROP CONSTRAINT IF EXISTS uq_credit_profiles_scope;
ALTER TABLE IF EXISTS ONLY public.credit_events DROP CONSTRAINT IF EXISTS uq_credit_events_scope;
ALTER TABLE IF EXISTS ONLY public.course_templates DROP CONSTRAINT IF EXISTS uq_course_templates_template_code;
ALTER TABLE IF EXISTS ONLY public.course_template_versions DROP CONSTRAINT IF EXISTS uq_course_template_versions_version;
ALTER TABLE IF EXISTS ONLY public.course_template_modules DROP CONSTRAINT IF EXISTS uq_course_template_modules_code;
ALTER TABLE IF EXISTS ONLY public.classrooms DROP CONSTRAINT IF EXISTS uq_classrooms_classroom_code;
ALTER TABLE IF EXISTS ONLY public.classroom_teachers DROP CONSTRAINT IF EXISTS uq_classroom_teachers_scope;
ALTER TABLE IF EXISTS ONLY public.classroom_students DROP CONSTRAINT IF EXISTS uq_classroom_students_scope;
ALTER TABLE IF EXISTS ONLY public.classroom_policy_versions DROP CONSTRAINT IF EXISTS uq_classroom_policy_versions_version;
ALTER TABLE IF EXISTS ONLY public.classroom_policy_versions DROP CONSTRAINT IF EXISTS uq_classroom_policy_versions_scope;
ALTER TABLE IF EXISTS ONLY public.classroom_modules DROP CONSTRAINT IF EXISTS uq_classroom_modules_scope;
ALTER TABLE IF EXISTS ONLY public.classroom_modules DROP CONSTRAINT IF EXISTS uq_classroom_modules_code;
ALTER TABLE IF EXISTS ONLY public.classroom_characters DROP CONSTRAINT IF EXISTS uq_classroom_characters_scope;
ALTER TABLE IF EXISTS ONLY public.classroom_characters DROP CONSTRAINT IF EXISTS uq_classroom_characters_catalog;
ALTER TABLE IF EXISTS ONLY public.character_equipment DROP CONSTRAINT IF EXISTS uq_character_equipment_scope;
ALTER TABLE IF EXISTS ONLY public.character_equipment DROP CONSTRAINT IF EXISTS uq_character_equipment_profile_slot;
ALTER TABLE IF EXISTS ONLY public.character_equipment DROP CONSTRAINT IF EXISTS uq_character_equipment_inventory_item;
ALTER TABLE IF EXISTS ONLY public.character_catalog DROP CONSTRAINT IF EXISTS uq_character_catalog_code;
ALTER TABLE IF EXISTS ONLY public.autonomous_schedules DROP CONSTRAINT IF EXISTS uq_autonomous_schedules_time;
ALTER TABLE IF EXISTS ONLY public.autonomous_schedules DROP CONSTRAINT IF EXISTS uq_autonomous_schedules_scope;
ALTER TABLE IF EXISTS ONLY public.autonomous_schedule_modules DROP CONSTRAINT IF EXISTS uq_autonomous_schedule_modules_scope;
ALTER TABLE IF EXISTS ONLY public.autonomous_schedule_modules DROP CONSTRAINT IF EXISTS uq_autonomous_schedule_modules_module;
ALTER TABLE IF EXISTS ONLY public.async_operations DROP CONSTRAINT IF EXISTS uq_async_operations_scope;
ALTER TABLE IF EXISTS ONLY public.ai_runs DROP CONSTRAINT IF EXISTS uq_ai_runs_scope;
ALTER TABLE IF EXISTS ONLY public.ai_runs DROP CONSTRAINT IF EXISTS uq_ai_runs_async_operation;
ALTER TABLE IF EXISTS ONLY public.ai_proposals DROP CONSTRAINT IF EXISTS uq_ai_proposals_scope;
ALTER TABLE IF EXISTS ONLY public.ai_decisions DROP CONSTRAINT IF EXISTS uq_ai_decisions_scope;
ALTER TABLE IF EXISTS ONLY public.ai_artifacts DROP CONSTRAINT IF EXISTS uq_ai_artifacts_scope;
ALTER TABLE IF EXISTS ONLY public.activity_sessions DROP CONSTRAINT IF EXISTS uq_activity_sessions_scope;
ALTER TABLE IF EXISTS ONLY public.activity_presentation_slides DROP CONSTRAINT IF EXISTS uq_activity_presentation_slides_sequence;
ALTER TABLE IF EXISTS ONLY public.activity_participants DROP CONSTRAINT IF EXISTS uq_activity_participants_student;
ALTER TABLE IF EXISTS ONLY public.activity_participants DROP CONSTRAINT IF EXISTS uq_activity_participants_scope;
ALTER TABLE IF EXISTS ONLY public.transaction_inquiries DROP CONSTRAINT IF EXISTS transaction_inquiries_pkey;
ALTER TABLE IF EXISTS ONLY public.transaction_groups DROP CONSTRAINT IF EXISTS transaction_groups_pkey;
ALTER TABLE IF EXISTS ONLY public.transaction_business_refs DROP CONSTRAINT IF EXISTS transaction_business_refs_pkey;
ALTER TABLE IF EXISTS ONLY public.trades DROP CONSTRAINT IF EXISTS trades_pkey;
ALTER TABLE IF EXISTS ONLY public.trade_money_legs DROP CONSTRAINT IF EXISTS trade_money_legs_pkey;
ALTER TABLE IF EXISTS ONLY public.trade_item_legs DROP CONSTRAINT IF EXISTS trade_item_legs_pkey;
ALTER TABLE IF EXISTS ONLY public.trade_decisions DROP CONSTRAINT IF EXISTS trade_decisions_pkey;
ALTER TABLE IF EXISTS ONLY public.teacher_credentials DROP CONSTRAINT IF EXISTS teacher_credentials_pkey;
ALTER TABLE IF EXISTS ONLY public.subscription_offers DROP CONSTRAINT IF EXISTS subscription_offers_pkey;
ALTER TABLE IF EXISTS ONLY public.subscription_offer_properties DROP CONSTRAINT IF EXISTS subscription_offer_properties_pkey;
ALTER TABLE IF EXISTS ONLY public.subscription_auto_deposit_policies DROP CONSTRAINT IF EXISTS subscription_auto_deposit_policies_pkey;
ALTER TABLE IF EXISTS ONLY public.subscription_applications DROP CONSTRAINT IF EXISTS subscription_applications_pkey;
ALTER TABLE IF EXISTS ONLY public.subscription_accounts DROP CONSTRAINT IF EXISTS subscription_accounts_pkey;
ALTER TABLE IF EXISTS ONLY public.subscription_account_deposits DROP CONSTRAINT IF EXISTS subscription_account_deposits_pkey;
ALTER TABLE IF EXISTS ONLY public.student_sessions DROP CONSTRAINT IF EXISTS student_sessions_pkey;
ALTER TABLE IF EXISTS ONLY public.student_credentials DROP CONSTRAINT IF EXISTS student_credentials_pkey;
ALTER TABLE IF EXISTS ONLY public.student_character_profiles DROP CONSTRAINT IF EXISTS student_character_profiles_pkey;
ALTER TABLE IF EXISTS ONLY public.store_stocks DROP CONSTRAINT IF EXISTS store_stocks_pkey;
ALTER TABLE IF EXISTS ONLY public.store_products DROP CONSTRAINT IF EXISTS store_products_pkey;
ALTER TABLE IF EXISTS ONLY public.store_product_versions DROP CONSTRAINT IF EXISTS store_product_versions_pkey;
ALTER TABLE IF EXISTS ONLY public.store_orders DROP CONSTRAINT IF EXISTS store_orders_pkey;
ALTER TABLE IF EXISTS ONLY public.store_order_items DROP CONSTRAINT IF EXISTS store_order_items_pkey;
ALTER TABLE IF EXISTS ONLY public.state_transitions DROP CONSTRAINT IF EXISTS state_transitions_pkey;
ALTER TABLE IF EXISTS ONLY public.schools DROP CONSTRAINT IF EXISTS schools_pkey;
ALTER TABLE IF EXISTS ONLY public.school_memberships DROP CONSTRAINT IF EXISTS school_memberships_pkey;
ALTER TABLE IF EXISTS ONLY public.school_ai_setting_versions DROP CONSTRAINT IF EXISTS school_ai_setting_versions_pkey;
ALTER TABLE IF EXISTS ONLY public.savings_installments DROP CONSTRAINT IF EXISTS savings_installments_pkey;
ALTER TABLE IF EXISTS ONLY public.savings_accounts DROP CONSTRAINT IF EXISTS savings_accounts_pkey;
ALTER TABLE IF EXISTS ONLY public.salary_roles DROP CONSTRAINT IF EXISTS salary_roles_pkey;
ALTER TABLE IF EXISTS ONLY public.report_snapshots DROP CONSTRAINT IF EXISTS report_snapshots_pkey;
ALTER TABLE IF EXISTS ONLY public.regrading_runs DROP CONSTRAINT IF EXISTS regrading_runs_pkey;
ALTER TABLE IF EXISTS ONLY public.realtime_streams DROP CONSTRAINT IF EXISTS realtime_streams_pkey;
ALTER TABLE IF EXISTS ONLY public.quiz_submissions DROP CONSTRAINT IF EXISTS quiz_submissions_pkey;
ALTER TABLE IF EXISTS ONLY public.quiz_student_results DROP CONSTRAINT IF EXISTS quiz_student_results_pkey;
ALTER TABLE IF EXISTS ONLY public.quiz_sessions DROP CONSTRAINT IF EXISTS quiz_sessions_pkey;
ALTER TABLE IF EXISTS ONLY public.quiz_session_questions DROP CONSTRAINT IF EXISTS quiz_session_questions_pkey;
ALTER TABLE IF EXISTS ONLY public.quiz_result_versions DROP CONSTRAINT IF EXISTS quiz_result_versions_pkey;
ALTER TABLE IF EXISTS ONLY public.quiz_questions DROP CONSTRAINT IF EXISTS quiz_questions_pkey;
ALTER TABLE IF EXISTS ONLY public.quiz_question_versions DROP CONSTRAINT IF EXISTS quiz_question_versions_pkey;
ALTER TABLE IF EXISTS ONLY public.quiz_question_issues DROP CONSTRAINT IF EXISTS quiz_question_issues_pkey;
ALTER TABLE IF EXISTS ONLY public.quiz_options DROP CONSTRAINT IF EXISTS quiz_options_pkey;
ALTER TABLE IF EXISTS ONLY public.quiz_answers DROP CONSTRAINT IF EXISTS quiz_answers_pkey;
ALTER TABLE IF EXISTS ONLY public.property_sale_listings DROP CONSTRAINT IF EXISTS property_sale_listings_pkey;
ALTER TABLE IF EXISTS ONLY public.property_lease_listings DROP CONSTRAINT IF EXISTS property_lease_listings_pkey;
ALTER TABLE IF EXISTS ONLY public.property_layouts DROP CONSTRAINT IF EXISTS property_layouts_pkey;
ALTER TABLE IF EXISTS ONLY public.property_contracts DROP CONSTRAINT IF EXISTS property_contracts_pkey;
ALTER TABLE IF EXISTS ONLY public.property_contract_payments DROP CONSTRAINT IF EXISTS property_contract_payments_pkey;
ALTER TABLE IF EXISTS ONLY public.properties DROP CONSTRAINT IF EXISTS properties_pkey;
ALTER TABLE IF EXISTS ONLY public.policy_snapshots DROP CONSTRAINT IF EXISTS policy_snapshots_pkey;
ALTER TABLE IF EXISTS ONLY public.policy_snapshot_sources DROP CONSTRAINT IF EXISTS policy_snapshot_sources_pkey;
ALTER TABLE IF EXISTS ONLY public.payslips DROP CONSTRAINT IF EXISTS payslips_pkey;
ALTER TABLE IF EXISTS ONLY public.outbox_events DROP CONSTRAINT IF EXISTS outbox_events_pkey;
ALTER TABLE IF EXISTS ONLY public.loans DROP CONSTRAINT IF EXISTS loans_pkey;
ALTER TABLE IF EXISTS ONLY public.loan_policy_versions DROP CONSTRAINT IF EXISTS loan_policy_versions_pkey;
ALTER TABLE IF EXISTS ONLY public.loan_installments DROP CONSTRAINT IF EXISTS loan_installments_pkey;
ALTER TABLE IF EXISTS ONLY public.loan_applications DROP CONSTRAINT IF EXISTS loan_applications_pkey;
ALTER TABLE IF EXISTS ONLY public.ledger_entries DROP CONSTRAINT IF EXISTS ledger_entries_pkey;
ALTER TABLE IF EXISTS ONLY public.ledger_accounts DROP CONSTRAINT IF EXISTS ledger_accounts_pkey;
ALTER TABLE IF EXISTS ONLY public.investment_scenario_versions DROP CONSTRAINT IF EXISTS investment_scenario_versions_pkey;
ALTER TABLE IF EXISTS ONLY public.investment_price_steps DROP CONSTRAINT IF EXISTS investment_price_steps_pkey;
ALTER TABLE IF EXISTS ONLY public.investment_positions DROP CONSTRAINT IF EXISTS investment_positions_pkey;
ALTER TABLE IF EXISTS ONLY public.investment_orders DROP CONSTRAINT IF EXISTS investment_orders_pkey;
ALTER TABLE IF EXISTS ONLY public.investment_markets DROP CONSTRAINT IF EXISTS investment_markets_pkey;
ALTER TABLE IF EXISTS ONLY public.investment_assets DROP CONSTRAINT IF EXISTS investment_assets_pkey;
ALTER TABLE IF EXISTS ONLY public.inventory_items DROP CONSTRAINT IF EXISTS inventory_items_pkey;
ALTER TABLE IF EXISTS ONLY public.idempotency_records DROP CONSTRAINT IF EXISTS idempotency_records_pkey;
ALTER TABLE IF EXISTS ONLY public.flyway_schema_history DROP CONSTRAINT IF EXISTS flyway_schema_history_pk;
ALTER TABLE IF EXISTS ONLY public.financial_products DROP CONSTRAINT IF EXISTS financial_products_pkey;
ALTER TABLE IF EXISTS ONLY public.financial_product_versions DROP CONSTRAINT IF EXISTS financial_product_versions_pkey;
ALTER TABLE IF EXISTS ONLY public.export_operations DROP CONSTRAINT IF EXISTS export_operations_pkey;
ALTER TABLE IF EXISTS ONLY public.economic_event_drafts DROP CONSTRAINT IF EXISTS economic_event_drafts_pkey;
ALTER TABLE IF EXISTS ONLY public.device_links DROP CONSTRAINT IF EXISTS device_links_pkey;
ALTER TABLE IF EXISTS ONLY public.deposit_accounts DROP CONSTRAINT IF EXISTS deposit_accounts_pkey;
ALTER TABLE IF EXISTS ONLY public.credit_profiles DROP CONSTRAINT IF EXISTS credit_profiles_pkey;
ALTER TABLE IF EXISTS ONLY public.credit_events DROP CONSTRAINT IF EXISTS credit_events_pkey;
ALTER TABLE IF EXISTS ONLY public.course_templates DROP CONSTRAINT IF EXISTS course_templates_pkey;
ALTER TABLE IF EXISTS ONLY public.course_template_versions DROP CONSTRAINT IF EXISTS course_template_versions_pkey;
ALTER TABLE IF EXISTS ONLY public.course_template_modules DROP CONSTRAINT IF EXISTS course_template_modules_pkey;
ALTER TABLE IF EXISTS ONLY public.classrooms DROP CONSTRAINT IF EXISTS classrooms_pkey;
ALTER TABLE IF EXISTS ONLY public.classroom_teachers DROP CONSTRAINT IF EXISTS classroom_teachers_pkey;
ALTER TABLE IF EXISTS ONLY public.classroom_students DROP CONSTRAINT IF EXISTS classroom_students_pkey;
ALTER TABLE IF EXISTS ONLY public.classroom_policy_versions DROP CONSTRAINT IF EXISTS classroom_policy_versions_pkey;
ALTER TABLE IF EXISTS ONLY public.classroom_modules DROP CONSTRAINT IF EXISTS classroom_modules_pkey;
ALTER TABLE IF EXISTS ONLY public.classroom_characters DROP CONSTRAINT IF EXISTS classroom_characters_pkey;
ALTER TABLE IF EXISTS ONLY public.character_equipment DROP CONSTRAINT IF EXISTS character_equipment_pkey;
ALTER TABLE IF EXISTS ONLY public.character_catalog DROP CONSTRAINT IF EXISTS character_catalog_pkey;
ALTER TABLE IF EXISTS ONLY public.autonomous_schedules DROP CONSTRAINT IF EXISTS autonomous_schedules_pkey;
ALTER TABLE IF EXISTS ONLY public.autonomous_schedule_modules DROP CONSTRAINT IF EXISTS autonomous_schedule_modules_pkey;
ALTER TABLE IF EXISTS ONLY public.audit_events DROP CONSTRAINT IF EXISTS audit_events_pkey;
ALTER TABLE IF EXISTS ONLY public.async_operations DROP CONSTRAINT IF EXISTS async_operations_pkey;
ALTER TABLE IF EXISTS ONLY public.ai_runs DROP CONSTRAINT IF EXISTS ai_runs_pkey;
ALTER TABLE IF EXISTS ONLY public.ai_proposals DROP CONSTRAINT IF EXISTS ai_proposals_pkey;
ALTER TABLE IF EXISTS ONLY public.ai_decisions DROP CONSTRAINT IF EXISTS ai_decisions_pkey;
ALTER TABLE IF EXISTS ONLY public.ai_artifacts DROP CONSTRAINT IF EXISTS ai_artifacts_pkey;
ALTER TABLE IF EXISTS ONLY public.activity_sessions DROP CONSTRAINT IF EXISTS activity_sessions_pkey;
ALTER TABLE IF EXISTS ONLY public.activity_presentation_slides DROP CONSTRAINT IF EXISTS activity_presentation_slides_pkey;
ALTER TABLE IF EXISTS ONLY public.activity_participants DROP CONSTRAINT IF EXISTS activity_participants_pkey;
DROP TABLE IF EXISTS public.votes;
DROP TABLE IF EXISTS public.vote_eligibilities;
DROP TABLE IF EXISTS public.users;
DROP TABLE IF EXISTS public.transaction_inquiries;
DROP TABLE IF EXISTS public.transaction_groups;
DROP TABLE IF EXISTS public.transaction_business_refs;
DROP TABLE IF EXISTS public.trades;
DROP TABLE IF EXISTS public.trade_money_legs;
DROP TABLE IF EXISTS public.trade_item_legs;
DROP TABLE IF EXISTS public.trade_decisions;
DROP TABLE IF EXISTS public.teacher_credentials;
DROP TABLE IF EXISTS public.subscription_offers;
DROP TABLE IF EXISTS public.subscription_offer_properties;
DROP TABLE IF EXISTS public.subscription_auto_deposit_policies;
DROP TABLE IF EXISTS public.subscription_applications;
DROP TABLE IF EXISTS public.subscription_accounts;
DROP TABLE IF EXISTS public.subscription_account_deposits;
DROP TABLE IF EXISTS public.student_sessions;
DROP TABLE IF EXISTS public.student_credentials;
DROP TABLE IF EXISTS public.store_stocks;
DROP TABLE IF EXISTS public.store_products;
DROP TABLE IF EXISTS public.store_product_versions;
DROP TABLE IF EXISTS public.store_orders;
DROP TABLE IF EXISTS public.store_order_items;
DROP TABLE IF EXISTS public.state_transitions;
DROP TABLE IF EXISTS public.schools;
DROP TABLE IF EXISTS public.school_memberships;
DROP TABLE IF EXISTS public.school_ai_setting_versions;
DROP TABLE IF EXISTS public.savings_installments;
DROP TABLE IF EXISTS public.savings_accounts;
DROP TABLE IF EXISTS public.salary_roles;
DROP TABLE IF EXISTS public.report_snapshots;
DROP TABLE IF EXISTS public.regrading_runs;
DROP TABLE IF EXISTS public.realtime_streams;
DROP TABLE IF EXISTS public.quiz_submissions;
DROP TABLE IF EXISTS public.quiz_session_questions;
DROP TABLE IF EXISTS public.quiz_questions;
DROP TABLE IF EXISTS public.quiz_question_versions;
DROP TABLE IF EXISTS public.quiz_question_issues;
DROP TABLE IF EXISTS public.quiz_options;
DROP TABLE IF EXISTS public.quiz_answers;
DROP VIEW IF EXISTS public.public_vote_totals;
DROP TABLE IF EXISTS public.vote_options;
DROP TABLE IF EXISTS public.vote_ballots;
DROP VIEW IF EXISTS public.public_quiz_leaderboard;
DROP TABLE IF EXISTS public.student_character_profiles;
DROP TABLE IF EXISTS public.quiz_student_results;
DROP TABLE IF EXISTS public.quiz_sessions;
DROP TABLE IF EXISTS public.quiz_result_versions;
DROP VIEW IF EXISTS public.public_investment_prices;
DROP VIEW IF EXISTS public.public_classroom_fund_balances;
DROP TABLE IF EXISTS public.property_sale_listings;
DROP TABLE IF EXISTS public.property_lease_listings;
DROP TABLE IF EXISTS public.property_layouts;
DROP TABLE IF EXISTS public.property_contracts;
DROP TABLE IF EXISTS public.property_contract_payments;
DROP TABLE IF EXISTS public.properties;
DROP TABLE IF EXISTS public.policy_snapshots;
DROP TABLE IF EXISTS public.policy_snapshot_sources;
DROP TABLE IF EXISTS public.payslips;
DROP TABLE IF EXISTS public.outbox_events;
DROP TABLE IF EXISTS public.loans;
DROP TABLE IF EXISTS public.loan_policy_versions;
DROP TABLE IF EXISTS public.loan_installments;
DROP TABLE IF EXISTS public.loan_applications;
DROP TABLE IF EXISTS public.ledger_entries;
DROP TABLE IF EXISTS public.ledger_accounts;
DROP TABLE IF EXISTS public.investment_scenario_versions;
DROP TABLE IF EXISTS public.investment_price_steps;
DROP TABLE IF EXISTS public.investment_positions;
DROP TABLE IF EXISTS public.investment_orders;
DROP TABLE IF EXISTS public.investment_markets;
DROP TABLE IF EXISTS public.investment_assets;
DROP TABLE IF EXISTS public.inventory_items;
DROP TABLE IF EXISTS public.idempotency_records;
DROP TABLE IF EXISTS public.flyway_schema_history;
DROP TABLE IF EXISTS public.financial_products;
DROP TABLE IF EXISTS public.financial_product_versions;
DROP TABLE IF EXISTS public.export_operations;
DROP TABLE IF EXISTS public.economic_event_drafts;
DROP TABLE IF EXISTS public.device_links;
DROP TABLE IF EXISTS public.deposit_accounts;
DROP TABLE IF EXISTS public.credit_profiles;
DROP TABLE IF EXISTS public.credit_events;
DROP TABLE IF EXISTS public.course_templates;
DROP TABLE IF EXISTS public.course_template_versions;
DROP TABLE IF EXISTS public.course_template_modules;
DROP TABLE IF EXISTS public.classrooms;
DROP TABLE IF EXISTS public.classroom_teachers;
DROP TABLE IF EXISTS public.classroom_students;
DROP TABLE IF EXISTS public.classroom_policy_versions;
DROP TABLE IF EXISTS public.classroom_modules;
DROP TABLE IF EXISTS public.classroom_characters;
DROP TABLE IF EXISTS public.character_equipment;
DROP TABLE IF EXISTS public.character_catalog;
DROP TABLE IF EXISTS public.autonomous_schedules;
DROP TABLE IF EXISTS public.autonomous_schedule_modules;
DROP TABLE IF EXISTS public.audit_events;
DROP TABLE IF EXISTS public.async_operations;
DROP TABLE IF EXISTS public.ai_runs;
DROP TABLE IF EXISTS public.ai_proposals;
DROP TABLE IF EXISTS public.ai_decisions;
DROP TABLE IF EXISTS public.ai_artifacts;
DROP TABLE IF EXISTS public.activity_sessions;
DROP TABLE IF EXISTS public.activity_presentation_slides;
DROP TABLE IF EXISTS public.activity_participants;
DROP FUNCTION IF EXISTS public.validate_character_equipment_item();
DROP FUNCTION IF EXISTS public.seed_default_character_store(target_classroom_id uuid, teacher_user_id uuid);
DROP FUNCTION IF EXISTS public.seed_character_store_for_teacher();
DROP FUNCTION IF EXISTS public.reject_row_mutation();
DROP FUNCTION IF EXISTS public.reject_mutation_when_published();
DROP FUNCTION IF EXISTS public.protect_published_quiz_student_result();
DROP FUNCTION IF EXISTS public.protect_outbox_event();
DROP FUNCTION IF EXISTS public.protect_committed_transaction_group();
DROP FUNCTION IF EXISTS public.prevent_equipped_inventory_lock();
DROP FUNCTION IF EXISTS public.prevent_classroom_identity_update();
DROP FUNCTION IF EXISTS public.normalize_transaction_group_recovery();
DROP FUNCTION IF EXISTS public.default_character_sort_order(character_code character varying);
DROP FUNCTION IF EXISTS public.default_character_price(character_code character varying);
DROP FUNCTION IF EXISTS public.default_character_description(character_code character varying);
DROP FUNCTION IF EXISTS public.classroom_accepts_new_operations(target_classroom_id uuid);
DROP FUNCTION IF EXISTS public.assert_published_quiz_has_correct_option();
DROP FUNCTION IF EXISTS public.assert_inventory_single_active_trade();
DROP FUNCTION IF EXISTS public.assert_committed_transaction_balanced();
--
-- Name: assert_committed_transaction_balanced(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.assert_committed_transaction_balanced() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    target_group_id uuid;
    target_classroom_id uuid;
    target_status varchar(40);
    entry_count bigint;
    amount_sum numeric;
    mismatch_count bigint;
BEGIN
    IF TG_TABLE_NAME = 'transaction_groups' THEN
        target_group_id := coalesce(NEW.id, OLD.id);
        target_classroom_id := coalesce(NEW.classroom_id, OLD.classroom_id);
    ELSE
        target_group_id := coalesce(NEW.transaction_group_id, OLD.transaction_group_id);
        target_classroom_id := coalesce(NEW.classroom_id, OLD.classroom_id);
    END IF;

    SELECT status INTO target_status
      FROM transaction_groups
     WHERE classroom_id = target_classroom_id
       AND id = target_group_id;

    IF target_status IS DISTINCT FROM 'COMMITTED' THEN
        RETURN NULL;
    END IF;

    SELECT count(*), coalesce(sum(amount), 0)
      INTO entry_count, amount_sum
      FROM ledger_entries
     WHERE classroom_id = target_classroom_id
       AND transaction_group_id = target_group_id;

    IF entry_count < 2 OR amount_sum <> 0 THEN
        RAISE EXCEPTION 'committed transaction % is unbalanced: count %, sum %',
            target_group_id, entry_count, amount_sum;
    END IF;

    SELECT count(*)
      INTO mismatch_count
      FROM ledger_entries entry
      JOIN ledger_accounts account
        ON account.classroom_id = entry.classroom_id
       AND account.id = entry.ledger_account_id
     WHERE entry.classroom_id = target_classroom_id
       AND entry.transaction_group_id = target_group_id
       AND (
           entry.balance_before + entry.amount <> entry.balance_after
           OR entry.account_sequence <> account.next_entry_sequence - 1
           OR entry.balance_after <> account.current_balance
       );

    IF mismatch_count <> 0 THEN
        RAISE EXCEPTION 'committed transaction % has stale account sequence or balance',
            target_group_id;
    END IF;
    RETURN NULL;
END;
$$;


--
-- Name: assert_inventory_single_active_trade(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.assert_inventory_single_active_trade() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    target_classroom_id uuid;
BEGIN
    target_classroom_id := coalesce(NEW.classroom_id, OLD.classroom_id);

    IF EXISTS (
        SELECT 1
          FROM trade_item_legs leg
          JOIN trades trade
            ON trade.classroom_id = leg.classroom_id
           AND trade.id = leg.trade_id
         WHERE leg.classroom_id = target_classroom_id
           AND trade.status IN ('AWAITING_COUNTERPART', 'AWAITING_TEACHER', 'SETTLING')
         GROUP BY leg.inventory_item_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'inventory item is referenced by multiple active trades';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM trade_item_legs leg
          JOIN trades trade
            ON trade.classroom_id = leg.classroom_id
           AND trade.id = leg.trade_id
          JOIN inventory_items item
            ON item.classroom_id = leg.classroom_id
           AND item.id = leg.inventory_item_id
         WHERE leg.classroom_id = target_classroom_id
           AND trade.status IN ('AWAITING_COUNTERPART', 'AWAITING_TEACHER', 'SETTLING')
           AND (
               item.status <> 'LOCKED_TRADE'
               OR item.locked_trade_id <> trade.id
               OR item.classroom_student_id <> leg.from_student_id
           )
    ) THEN
        RAISE EXCEPTION 'active trade item lock or ownership is inconsistent';
    END IF;
    RETURN NULL;
END;
$$;


--
-- Name: assert_published_quiz_has_correct_option(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.assert_published_quiz_has_correct_option() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    target_version_id uuid;
    target_classroom_id uuid;
    target_status varchar(40);
BEGIN
    IF TG_TABLE_NAME = 'quiz_question_versions' THEN
        target_version_id := coalesce(NEW.id, OLD.id);
        target_classroom_id := coalesce(NEW.classroom_id, OLD.classroom_id);
    ELSE
        target_version_id := coalesce(NEW.quiz_question_version_id, OLD.quiz_question_version_id);
        target_classroom_id := coalesce(NEW.classroom_id, OLD.classroom_id);
    END IF;
    SELECT status INTO target_status
      FROM quiz_question_versions
     WHERE classroom_id = target_classroom_id AND id = target_version_id;
    IF target_status = 'PUBLISHED'
       AND NOT EXISTS (
           SELECT 1 FROM quiz_options
            WHERE classroom_id = target_classroom_id
              AND quiz_question_version_id = target_version_id
              AND is_correct
       ) THEN
        RAISE EXCEPTION 'published quiz question must have a correct option';
    END IF;
    RETURN NULL;
END;
$$;


--
-- Name: classroom_accepts_new_operations(uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.classroom_accepts_new_operations(target_classroom_id uuid) RETURNS boolean
    LANGUAGE sql STABLE
    AS $$
    SELECT EXISTS (
        SELECT 1
          FROM classrooms
         WHERE id = target_classroom_id
           AND status = 'ACTIVE'
    );
$$;


--
-- Name: default_character_description(character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.default_character_description(character_code character varying) RETURNS text
    LANGUAGE sql IMMUTABLE
    AS $$
    SELECT CASE character_code
        -- 기본
        WHEN 'hat_red_cap' THEN '활기찬 빨간색이 돋보이는 스포티한 모자예요.'
        WHEN 'hat_teal_beanie' THEN '푹 눌러쓰는 청록색 비니예요. 머리가 폭 감싸여 따뜻해 보여요.'
        WHEN 'face_sunglasses' THEN '까만 렌즈가 반짝이는 선글라스예요. 쓰면 조금 멋있어 보여요.'
        WHEN 'face_headphones' THEN '귀를 감싸는 회색 헤드폰이에요. 음악을 듣는 것 같은 모습이 돼요.'
        WHEN 'top_blue_tshirt' THEN '시원한 파란색 티셔츠예요. 어떤 하의와도 잘 어울려요.'
        WHEN 'top_yellow_hoodie' THEN '포근한 느낌의 노란색 후드예요. 편안하고 밝은 분위기를 만들어 줘요.'
        WHEN 'top_green_vest' THEN '주머니가 달린 초록 조끼예요. 활동적인 느낌을 줘요.'
        WHEN 'bottom_overalls' THEN '멜빵이 달린 청색 바지예요. 씩씩한 느낌이 들어요.'
        WHEN 'shoes_sneakers' THEN '흰 바탕에 빨간 줄이 들어간 운동화예요. 달리고 싶어지는 신발이에요.'
        WHEN 'back_backpack' THEN '어깨끈이 보이는 갈색 배낭이에요. 소풍 가는 기분이 들어요.'

        -- 계절
        WHEN 'hat_straw' THEN '넓은 챙이 있는 밀짚모자예요. 햇볕이 쨍쨍한 날에 잘 어울려요.'
        WHEN 'hat_beach' THEN '파란 리본을 두른 크림색 비치 모자예요. 바닷가 분위기가 나요.'
        WHEN 'hat_pom_beanie' THEN '방울이 달린 초록 비니예요. 겨울에 쓰면 아주 따뜻해 보여요.'
        WHEN 'face_goggles' THEN '눈을 덮는 파란 물안경이에요. 물놀이 준비가 끝난 모습이 돼요.'
        WHEN 'face_ski_goggles' THEN '주황 렌즈가 넓은 스키 고글이에요. 눈밭을 달릴 것 같아요.'
        WHEN 'neck_scarf' THEN '목에 두르는 빨간 목도리예요. 끝이 살짝 내려와 귀여워요.'
        WHEN 'neck_knit_scarf' THEN '두툼하게 짠 초록 목도리예요. 목이 따뜻해 보여요.'
        WHEN 'top_swimsuit' THEN '어깨끈이 있는 파란 수영복이에요. 물놀이할 때 딱이에요.'
        WHEN 'top_padding' THEN '빵빵한 빨간 패딩 점퍼예요. 아주 추운 날에도 든든해 보여요.'
        WHEN 'shoes_red_boots' THEN '발목까지 올라오는 빨간 장화예요. 비 오는 날이 기다려져요.'
        WHEN 'shoes_sandals' THEN '발등에 끈이 있는 갈색 샌들이에요. 시원한 느낌이 들어요.'
        WHEN 'shoes_aqua' THEN '물이 잘 빠지는 하늘색 아쿠아슈즈예요. 물놀이할 때 편해요.'
        WHEN 'shoes_snow_boots' THEN '털이 달린 하얀 스노우 부츠예요. 눈길에서도 따뜻해 보여요.'
        WHEN 'effect_swim_tube' THEN '허리에 끼우는 주황색 튜브예요. 물에 뜬 것 같은 모습이 돼요.'
        WHEN 'bg_spring' THEN '분홍 벚꽃이 활짝 핀 봄 풍경이에요. 배경이 환해져요.'
        WHEN 'bg_summer_sea' THEN '파란 바다와 모래사장이 보이는 여름 풍경이에요.'
        WHEN 'bg_autumn' THEN '노랗고 빨갛게 물든 가을 나무가 있는 풍경이에요.'
        WHEN 'bg_winter' THEN '눈이 내린 겨울 숲 풍경이에요. 조용하고 시원해 보여요.'

        -- 스포츠
        WHEN 'hat_baseball_blue' THEN '앞에 흰 마크가 있는 파란 야구 모자예요.'
        WHEN 'hat_baseball_red' THEN '앞에 흰 마크가 있는 빨간 야구 모자예요.'
        WHEN 'hat_baseball_navy' THEN '앞에 흰 마크가 있는 남색 야구 모자예요.'
        WHEN 'hat_baseball_green' THEN '앞에 흰 마크가 있는 초록 야구 모자예요.'
        WHEN 'hat_baseball_yellow' THEN '앞에 흰 마크가 있는 노란 야구 모자예요.'
        WHEN 'top_soccer' THEN '등번호가 들어간 주황 축구 유니폼이에요. 운동장이 생각나요.'
        WHEN 'top_baseball_pinstripe' THEN '세로 줄무늬가 들어간 흰 야구복이에요. 단정해 보여요.'
        WHEN 'top_baseball_red' THEN '단추가 줄지어 달린 빨간 야구복이에요.'
        WHEN 'top_baseball_blue' THEN '단추가 줄지어 달린 파란 야구복이에요.'
        WHEN 'top_baseball_navy' THEN '단추가 줄지어 달린 남색 야구복이에요.'
        WHEN 'top_baseball_gray' THEN '원정 경기에서 입는 회색 야구복이에요.'
        WHEN 'hand_baseball_glove' THEN '손에 끼우는 갈색 야구 글러브예요. 공을 받을 준비가 돼요.'

        -- 직업
        WHEN 'hat_chef' THEN '높고 하얀 요리사 모자예요. 맛있는 요리를 만들 것 같아요.'
        WHEN 'hat_safari' THEN '챙이 넓은 탐험가 모자예요. 어디든 떠날 수 있을 것 같아요.'
        WHEN 'hat_space_helmet' THEN '얼굴이 보이는 둥근 우주 헬멧이에요. 우주로 나갈 준비가 돼요.'
        WHEN 'hat_safety_police' THEN '금색 띠를 두른 남색 안전 경찰 모자예요.'
        WHEN 'hat_pilot' THEN '금장이 달린 흰 파일럿 모자예요. 하늘을 날 것 같아요.'
        WHEN 'top_spacesuit' THEN '가슴에 조작판이 있는 흰 우주복이에요. 온몸을 감싸 줘요.'
        WHEN 'top_safety_police' THEN '배지가 달린 남색 안전 경찰복이에요. 씩씩해 보여요.'
        WHEN 'top_legal_professional' THEN '넥타이를 맨 검정 정장이에요. 아주 단정해 보여요.'
        WHEN 'top_pilot' THEN '어깨에 금장이 있는 흰 파일럿 제복이에요.'
        WHEN 'shoes_space_boots' THEN '두툼한 흰 우주 부츠예요. 발이 든든해 보여요.'
        WHEN 'back_briefcase' THEN '손에 드는 갈색 서류 가방이에요. 어른스러운 느낌이 나요.'
        WHEN 'bg_space' THEN '별이 반짝이는 우주 풍경이에요. 밤처럼 어둡고 조용해요.'

        -- 판타지
        WHEN 'hat_crown' THEN '보석이 박힌 금색 왕관이에요. 머리에 반짝임을 더해 줘요.'
        WHEN 'hat_wizard' THEN '별이 달린 뾰족한 보라색 마법사 모자예요.'
        WHEN 'hat_knight' THEN '얼굴을 감싸는 은색 기사 투구예요. 아주 단단해 보여요.'
        WHEN 'hat_dinosaur' THEN '등에 지느러미가 달린 초록 공룡 후드예요. 얼굴은 그대로 보여요.'
        WHEN 'top_wizard_robe' THEN '금색 장식이 있는 보라색 마법사 로브예요. 발까지 길게 내려와요.'
        WHEN 'top_fantasy_armor' THEN '가슴판이 달린 은색 갑옷이에요. 모험을 떠날 것 같아요.'
        WHEN 'top_dinosaur' THEN '배가 연두색인 초록 공룡 옷이에요. 온몸을 감싸 줘요.'
        WHEN 'back_fairy_wings' THEN '몸 뒤로 활짝 펴지는 하늘색 요정 날개예요.'
        WHEN 'back_dinosaur_tail' THEN '옆으로 길게 뻗은 초록 공룡 꼬리예요.'
        WHEN 'hand_magic_wand' THEN '끝에 금색 별이 달린 마법봉이에요. 주문을 외울 것 같아요.'
        WHEN 'hand_toy_sword' THEN '손잡이가 금색인 장난감 검이에요. 씩씩하게 들 수 있어요.'
        WHEN 'effect_fluffy_cloud' THEN '발밑에 깔리는 몽실몽실한 구름이에요. 구름 위에 선 것 같아요.'

        ELSE '내 캐릭터를 꾸며 주는 도트 아이템이에요.'
    END;
$$;


--
-- Name: default_character_price(character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.default_character_price(character_code character varying) RETURNS bigint
    LANGUAGE sql IMMUTABLE
    AS $$
    SELECT CASE character_code
        WHEN 'hat_crown' THEN 80
        WHEN 'bg_spring' THEN 80
        WHEN 'bg_summer_sea' THEN 80
        WHEN 'bg_autumn' THEN 80
        WHEN 'bg_winter' THEN 80
        WHEN 'bg_space' THEN 80
        WHEN 'top_spacesuit' THEN 60
        WHEN 'top_padding' THEN 55
        WHEN 'top_baseball_pinstripe' THEN 55
        WHEN 'top_legal_professional' THEN 55
        WHEN 'top_wizard_robe' THEN 55
        WHEN 'top_fantasy_armor' THEN 55
        WHEN 'top_dinosaur' THEN 55
        WHEN 'top_pilot' THEN 55
        WHEN 'top_baseball_red' THEN 50
        WHEN 'top_baseball_blue' THEN 50
        WHEN 'top_baseball_navy' THEN 50
        WHEN 'top_baseball_gray' THEN 50
        WHEN 'top_safety_police' THEN 50
        WHEN 'back_fairy_wings' THEN 50
        WHEN 'hat_space_helmet' THEN 45
        WHEN 'top_soccer' THEN 45
        WHEN 'effect_fluffy_cloud' THEN 45
        WHEN 'top_yellow_hoodie' THEN 45
        WHEN 'bottom_overalls' THEN 45
        WHEN 'top_swimsuit' THEN 40
        WHEN 'shoes_snow_boots' THEN 40
        WHEN 'shoes_space_boots' THEN 40
        WHEN 'hat_wizard' THEN 40
        WHEN 'hat_knight' THEN 40
        WHEN 'top_green_vest' THEN 40
        WHEN 'back_backpack' THEN 40
        WHEN 'hat_safari' THEN 35
        WHEN 'hat_pilot' THEN 35
        WHEN 'hat_dinosaur' THEN 35
        WHEN 'shoes_red_boots' THEN 35
        WHEN 'effect_swim_tube' THEN 35
        WHEN 'top_blue_tshirt' THEN 35
        WHEN 'face_headphones' THEN 35
        WHEN 'hat_pom_beanie' THEN 30
        WHEN 'hat_baseball_blue' THEN 30
        WHEN 'hat_baseball_red' THEN 30
        WHEN 'hat_baseball_navy' THEN 30
        WHEN 'hat_baseball_green' THEN 30
        WHEN 'hat_baseball_yellow' THEN 30
        WHEN 'hat_chef' THEN 30
        WHEN 'hat_safety_police' THEN 30
        WHEN 'back_briefcase' THEN 30
        WHEN 'back_dinosaur_tail' THEN 30
        WHEN 'hand_baseball_glove' THEN 30
        WHEN 'hand_magic_wand' THEN 30
        WHEN 'hand_toy_sword' THEN 30
        WHEN 'hat_red_cap' THEN 30
        WHEN 'hat_teal_beanie' THEN 30
        WHEN 'shoes_sneakers' THEN 30
        ELSE 25
    END;
$$;


--
-- Name: default_character_sort_order(character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.default_character_sort_order(character_code character varying) RETURNS integer
    LANGUAGE sql IMMUTABLE
    AS $$
    SELECT CASE character_code
        -- 기본 코디 한 벌이 '추천' 첫 화면에 오도록 앞쪽에 둔다.
        WHEN 'hat_red_cap' THEN 10
        WHEN 'top_blue_tshirt' THEN 20
        WHEN 'bottom_overalls' THEN 30
        WHEN 'shoes_sneakers' THEN 40
        WHEN 'bg_spring' THEN 50
        WHEN 'back_backpack' THEN 60
        WHEN 'top_yellow_hoodie' THEN 70
        WHEN 'hat_teal_beanie' THEN 80
        WHEN 'face_sunglasses' THEN 90
        WHEN 'top_green_vest' THEN 100
        WHEN 'face_headphones' THEN 110

        -- 계절
        WHEN 'hat_straw' THEN 120
        WHEN 'hat_beach' THEN 130
        WHEN 'hat_pom_beanie' THEN 140
        WHEN 'face_goggles' THEN 150
        WHEN 'face_ski_goggles' THEN 160
        WHEN 'neck_scarf' THEN 170
        WHEN 'neck_knit_scarf' THEN 180
        WHEN 'top_swimsuit' THEN 190
        WHEN 'top_padding' THEN 200
        WHEN 'shoes_red_boots' THEN 210
        WHEN 'shoes_sandals' THEN 220
        WHEN 'shoes_aqua' THEN 230
        WHEN 'shoes_snow_boots' THEN 240
        WHEN 'effect_swim_tube' THEN 250
        WHEN 'bg_summer_sea' THEN 260
        WHEN 'bg_autumn' THEN 270
        WHEN 'bg_winter' THEN 280

        -- 스포츠
        WHEN 'hat_baseball_blue' THEN 290
        WHEN 'hat_baseball_red' THEN 300
        WHEN 'hat_baseball_navy' THEN 310
        WHEN 'hat_baseball_green' THEN 320
        WHEN 'hat_baseball_yellow' THEN 330
        WHEN 'top_soccer' THEN 340
        WHEN 'top_baseball_pinstripe' THEN 350
        WHEN 'top_baseball_red' THEN 360
        WHEN 'top_baseball_blue' THEN 370
        WHEN 'top_baseball_navy' THEN 380
        WHEN 'top_baseball_gray' THEN 390
        WHEN 'hand_baseball_glove' THEN 400

        -- 직업
        WHEN 'hat_chef' THEN 410
        WHEN 'hat_safari' THEN 420
        WHEN 'hat_space_helmet' THEN 430
        WHEN 'hat_safety_police' THEN 440
        WHEN 'hat_pilot' THEN 450
        WHEN 'top_spacesuit' THEN 460
        WHEN 'top_safety_police' THEN 470
        WHEN 'top_legal_professional' THEN 480
        WHEN 'top_pilot' THEN 490
        WHEN 'shoes_space_boots' THEN 500
        WHEN 'back_briefcase' THEN 510
        WHEN 'bg_space' THEN 520

        -- 판타지
        WHEN 'hat_crown' THEN 530
        WHEN 'hat_wizard' THEN 540
        WHEN 'hat_knight' THEN 550
        WHEN 'hat_dinosaur' THEN 560
        WHEN 'top_wizard_robe' THEN 570
        WHEN 'top_fantasy_armor' THEN 580
        WHEN 'top_dinosaur' THEN 590
        WHEN 'back_fairy_wings' THEN 600
        WHEN 'back_dinosaur_tail' THEN 610
        WHEN 'hand_magic_wand' THEN 620
        WHEN 'hand_toy_sword' THEN 630
        WHEN 'effect_fluffy_cloud' THEN 640

        -- 새 상품이 들어와도 목록 끝에 붙기만 하고 기존 순서를 흔들지 않는다.
        ELSE 900
    END;
$$;


--
-- Name: normalize_transaction_group_recovery(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.normalize_transaction_group_recovery() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    original_status varchar(40);
    original_root uuid;
BEGIN
    IF NEW.recovery_of_group_id IS NULL THEN
        IF NEW.root_transaction_group_id IS NULL THEN
            NEW.root_transaction_group_id := NEW.id;
        ELSIF NEW.root_transaction_group_id <> NEW.id THEN
            RAISE EXCEPTION 'original transaction root must equal its id';
        END IF;
        RETURN NEW;
    END IF;

    IF NEW.recovery_of_group_id = NEW.id THEN
        RAISE EXCEPTION 'transaction cannot recover itself';
    END IF;

    SELECT status, coalesce(root_transaction_group_id, id)
      INTO original_status, original_root
      FROM transaction_groups
     WHERE classroom_id = NEW.classroom_id
       AND id = NEW.recovery_of_group_id;

    IF NOT FOUND OR original_status <> 'COMMITTED' THEN
        RAISE EXCEPTION 'recovery target must be a committed transaction';
    END IF;

    IF NEW.root_transaction_group_id IS NOT NULL
       AND NEW.root_transaction_group_id <> original_root THEN
        RAISE EXCEPTION 'recovery transaction must preserve the original root';
    END IF;
    NEW.root_transaction_group_id := original_root;
    RETURN NEW;
END;
$$;


--
-- Name: prevent_classroom_identity_update(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_classroom_identity_update() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.classroom_code IS DISTINCT FROM OLD.classroom_code THEN
        RAISE EXCEPTION 'classroom_code is immutable';
    END IF;
    IF NEW.currency_name IS DISTINCT FROM OLD.currency_name THEN
        RAISE EXCEPTION 'currency_name is immutable';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: prevent_equipped_inventory_lock(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_equipped_inventory_lock() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.status <> 'AVAILABLE'
       AND EXISTS (
           SELECT 1
             FROM character_equipment equipment
            WHERE equipment.classroom_id = NEW.classroom_id
              AND equipment.inventory_item_id = NEW.id
       ) THEN
        RAISE EXCEPTION 'equipped inventory item must be unequipped before trade lock';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: protect_committed_transaction_group(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_committed_transaction_group() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF OLD.status = 'COMMITTED' THEN
        RAISE EXCEPTION 'committed transaction groups are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: protect_outbox_event(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_outbox_event() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'outbox events cannot be deleted';
    END IF;
    IF NEW.event_id IS DISTINCT FROM OLD.event_id
       OR NEW.classroom_id IS DISTINCT FROM OLD.classroom_id
       OR NEW.realtime_stream_id IS DISTINCT FROM OLD.realtime_stream_id
       OR NEW.channel_sequence IS DISTINCT FROM OLD.channel_sequence
       OR NEW.event_type IS DISTINCT FROM OLD.event_type
       OR NEW.aggregate_type IS DISTINCT FROM OLD.aggregate_type
       OR NEW.aggregate_id IS DISTINCT FROM OLD.aggregate_id
       OR NEW.state_version IS DISTINCT FROM OLD.state_version
       OR NEW.payload IS DISTINCT FROM OLD.payload
       OR NEW.occurred_at IS DISTINCT FROM OLD.occurred_at THEN
        RAISE EXCEPTION 'outbox identity and payload are immutable';
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: protect_published_quiz_student_result(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.protect_published_quiz_student_result() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM quiz_result_versions result_version
         WHERE result_version.classroom_id = OLD.classroom_id
           AND result_version.id = OLD.quiz_result_version_id
           AND result_version.published_at IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'published quiz student results are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: reject_mutation_when_published(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.reject_mutation_when_published() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    old_json jsonb;
    new_json jsonb;
    old_status text;
    new_status text;
BEGIN
    old_json := to_jsonb(OLD);
    IF (old_json ->> TG_ARGV[0]) IS NULL THEN
        IF TG_OP = 'DELETE' THEN
            RETURN OLD;
        END IF;
        RETURN NEW;
    END IF;

    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION '% published rows cannot be deleted', TG_TABLE_NAME;
    END IF;

    new_json := to_jsonb(NEW);
    IF (new_json - ARRAY['updated_at', 'retired_at', 'superseded_at', 'status'])
       IS DISTINCT FROM
       (old_json - ARRAY['updated_at', 'retired_at', 'superseded_at', 'status']) THEN
        RAISE EXCEPTION '% published business fields are immutable', TG_TABLE_NAME;
    END IF;

    IF old_json ? 'status' THEN
        old_status := old_json ->> 'status';
        new_status := new_json ->> 'status';
        IF new_status IS DISTINCT FROM old_status
           AND new_status NOT IN ('RETIRED', 'SUPERSEDED') THEN
            RAISE EXCEPTION '% published status can only retire or supersede', TG_TABLE_NAME;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: reject_row_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.reject_row_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION '% rows are immutable after insertion', TG_TABLE_NAME;
END;
$$;


--
-- Name: seed_character_store_for_teacher(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.seed_character_store_for_teacher() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.ended_at IS NULL THEN
        PERFORM seed_default_character_store(NEW.classroom_id, NEW.user_id);
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: seed_default_character_store(uuid, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.seed_default_character_store(target_classroom_id uuid, teacher_user_id uuid) RETURNS void
    LANGUAGE plpgsql
    AS $$
BEGIN
    INSERT INTO classroom_characters (
        id,
        classroom_id,
        character_catalog_id,
        display_name,
        sort_order,
        status
    )
    SELECT
        md5(target_classroom_id::text || ':classroom-character:' || catalog.id::text)::uuid,
        target_classroom_id,
        catalog.id,
        catalog.name,
        default_character_sort_order(catalog.character_code),
        'ACTIVE'
    FROM character_catalog catalog
    WHERE catalog.rights_evidence_key = 'repo:code-pixel-catalog-v1'
      AND catalog.status IN ('ACTIVE', 'APPROVED')
    ON CONFLICT (classroom_id, character_catalog_id) DO NOTHING;

    INSERT INTO store_products (
        id,
        classroom_id,
        name,
        description,
        product_kind,
        created_by_user_id,
        status
    )
    SELECT
        md5(target_classroom_id::text || ':store-product:' || catalog.character_code)::uuid,
        target_classroom_id,
        catalog.name,
        default_character_description(catalog.character_code),
        'CHARACTER',
        teacher_user_id,
        'PUBLISHED'
    FROM character_catalog catalog
    WHERE catalog.rights_evidence_key = 'repo:code-pixel-catalog-v1'
      AND catalog.status IN ('ACTIVE', 'APPROVED')
    ON CONFLICT (id) DO NOTHING;

    INSERT INTO store_product_versions (
        id,
        classroom_id,
        store_product_id,
        version,
        product_kind,
        unit_price,
        vat_rate_bps,
        purchasable,
        metadata,
        published_at
    )
    SELECT
        md5(target_classroom_id::text || ':store-product-version:' || catalog.character_code)::uuid,
        target_classroom_id,
        md5(target_classroom_id::text || ':store-product:' || catalog.character_code)::uuid,
        1,
        'CHARACTER',
        default_character_price(catalog.character_code),
        0,
        true,
        jsonb_build_object('character_catalog_id', catalog.id),
        now()
    FROM character_catalog catalog
    WHERE catalog.rights_evidence_key = 'repo:code-pixel-catalog-v1'
      AND catalog.status IN ('ACTIVE', 'APPROVED')
    ON CONFLICT (store_product_id, version) DO NOTHING;

    INSERT INTO store_stocks (
        store_product_id,
        classroom_id,
        available_quantity,
        reserved_quantity
    )
    SELECT
        md5(target_classroom_id::text || ':store-product:' || catalog.character_code)::uuid,
        target_classroom_id,
        99,
        0
    FROM character_catalog catalog
    WHERE catalog.rights_evidence_key = 'repo:code-pixel-catalog-v1'
      AND catalog.status IN ('ACTIVE', 'APPROVED')
    ON CONFLICT (store_product_id) DO NOTHING;

    UPDATE classroom_characters classroom_character
       SET store_product_id = md5(
               target_classroom_id::text
               || ':store-product:'
               || catalog.character_code
           )::uuid,
           updated_at = now()
      FROM character_catalog catalog
     WHERE classroom_character.classroom_id = target_classroom_id
       AND classroom_character.character_catalog_id = catalog.id
       AND classroom_character.store_product_id IS NULL
       AND catalog.rights_evidence_key = 'repo:code-pixel-catalog-v1'
       AND catalog.status IN ('ACTIVE', 'APPROVED');
END;
$$;


--
-- Name: validate_character_equipment_item(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.validate_character_equipment_item() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    item_status varchar(40);
    item_slot varchar(40);
BEGIN
    SELECT item.status,
           CASE upper(split_part(catalog.character_code, '_', 1))
               WHEN 'BG' THEN 'BACKGROUND'
               ELSE upper(split_part(catalog.character_code, '_', 1))
           END
      INTO item_status, item_slot
      FROM inventory_items item
      JOIN character_catalog catalog ON catalog.id = item.character_catalog_id
     WHERE item.classroom_id = NEW.classroom_id
       AND item.classroom_student_id = NEW.classroom_student_id
       AND item.id = NEW.inventory_item_id
       FOR UPDATE OF item;

    IF NOT FOUND OR item_status <> 'AVAILABLE' THEN
        RAISE EXCEPTION 'only an available owned inventory item can be equipped';
    END IF;
    IF item_slot IS DISTINCT FROM NEW.slot THEN
        RAISE EXCEPTION 'equipment slot does not match character catalog code';
    END IF;
    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: activity_participants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.activity_participants (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    activity_session_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    participant_status character varying(40) NOT NULL,
    joined_at timestamp with time zone,
    completed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: activity_presentation_slides; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.activity_presentation_slides (
    id uuid NOT NULL,
    activity_session_id uuid NOT NULL,
    sequence_number integer NOT NULL,
    title character varying(120) NOT NULL,
    image_url text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_activity_presentation_slides_sequence CHECK ((sequence_number >= 1))
);


--
-- Name: activity_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.activity_sessions (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    activity_type character varying(40) NOT NULL,
    policy_snapshot_id uuid,
    created_by_user_id uuid NOT NULL,
    public_display_enabled boolean DEFAULT false NOT NULL,
    starts_at timestamp with time zone,
    ends_at timestamp with time zone,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    public_fields jsonb DEFAULT '[]'::jsonb NOT NULL,
    presentation_image_url text,
    presentation_title character varying(120),
    presentation_cursor integer,
    CONSTRAINT ck_activity_sessions_presentation_cursor CHECK (((presentation_cursor IS NULL) OR (presentation_cursor >= 1))),
    CONSTRAINT ck_activity_sessions_public_fields CHECK ((jsonb_typeof(public_fields) = 'array'::text)),
    CONSTRAINT ck_activity_sessions_status CHECK (((status)::text = ANY ((ARRAY['READY'::character varying, 'LIVE'::character varying, 'FINALIZING'::character varying, 'COMPLETED'::character varying, 'CANCELED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: ai_artifacts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_artifacts (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    ai_run_id uuid NOT NULL,
    artifact_type character varying(40) NOT NULL,
    content jsonb NOT NULL,
    schema_version character varying(20) NOT NULL,
    safety_status character varying(40) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: ai_decisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_decisions (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    ai_proposal_id uuid NOT NULL,
    decided_by_user_id uuid NOT NULL,
    decision character varying(40) NOT NULL,
    reason text,
    decided_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: ai_proposals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_proposals (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    ai_run_id uuid NOT NULL,
    proposal_type character varying(40) NOT NULL,
    proposed_values jsonb NOT NULL,
    allowed_ranges jsonb NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    applied_policy_version_id uuid,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    source_type character varying(40) NOT NULL,
    severity character varying(20),
    metric_code character varying(80),
    direction character varying(20),
    dedupe_key character varying(200),
    CONSTRAINT ck_ai_proposals_status CHECK (((status)::text = ANY ((ARRAY['AWAITING_TEACHER'::character varying, 'ACKNOWLEDGED'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'EXPIRED'::character varying, 'APPLIED'::character varying, 'APPLY_FAILED'::character varying])::text[])))
);


--
-- Name: ai_runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_runs (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    async_operation_id uuid,
    requested_by_user_id uuid,
    run_type character varying(40) NOT NULL,
    model_provider character varying(40),
    model_id character varying(120),
    prompt_version character varying(40),
    input_scope jsonb NOT NULL,
    validation_result jsonb,
    requested_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    error_code character varying(80),
    retention_until timestamp with time zone NOT NULL,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    trigger_type character varying(40) NOT NULL,
    analysis_period_from timestamp with time zone,
    analysis_period_to timestamp with time zone,
    CONSTRAINT ck_ai_runs_retention CHECK ((retention_until >= requested_at)),
    CONSTRAINT ck_ai_runs_status CHECK (((status)::text = ANY ((ARRAY['REQUESTED'::character varying, 'DATA_CHECK'::character varying, 'INSUFFICIENT_DATA'::character varying, 'GENERATING'::character varying, 'VALIDATING'::character varying, 'AWAITING_TEACHER'::character varying, 'ACKNOWLEDGED'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'EXPIRED'::character varying, 'APPLYING'::character varying, 'COMPLETED'::character varying, 'TIMED_OUT'::character varying, 'FAILED'::character varying, 'REJECTED_OUTPUT'::character varying, 'APPLY_FAILED'::character varying])::text[])))
);


--
-- Name: async_operations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.async_operations (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    operation_type character varying(40) NOT NULL,
    requested_by_user_id uuid,
    progress_percent integer DEFAULT 0 NOT NULL,
    result_location character varying(512),
    error_code character varying(80),
    error_detail text,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    expires_at timestamp with time zone,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_async_operations_progress CHECK (((progress_percent >= 0) AND (progress_percent <= 100)))
);


--
-- Name: audit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_events (
    id uuid NOT NULL,
    classroom_id uuid,
    transaction_group_id uuid,
    actor_type character varying(40) NOT NULL,
    actor_id uuid,
    action character varying(120) NOT NULL,
    target_type character varying(40) NOT NULL,
    target_id uuid,
    request_id character varying(128),
    correlation_id character varying(128),
    before_summary jsonb,
    after_summary jsonb,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    retention_until timestamp with time zone NOT NULL,
    CONSTRAINT ck_audit_events_retention CHECK ((retention_until >= occurred_at))
);


--
-- Name: autonomous_schedule_modules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.autonomous_schedule_modules (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    autonomous_schedule_id uuid NOT NULL,
    module_code character varying(40) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_autonomous_schedule_modules_canonical_code CHECK (((module_code)::text = ANY ((ARRAY['ROLE_SALARY'::character varying, 'INCOME_TAX'::character varying, 'STORE'::character varying, 'BUDGET'::character varying, 'PAYSLIP'::character varying, 'PUBLIC_FUND'::character varying, 'PUBLIC_BUDGET'::character varying, 'DEPOSIT'::character varying, 'SAVINGS'::character varying, 'CREDIT'::character varying, 'LOAN'::character varying, 'REAL_ESTATE'::character varying, 'HOUSING_SUBSCRIPTION'::character varying, 'INVESTMENT'::character varying, 'RISK_MANAGEMENT'::character varying, 'MARKET_PRICE'::character varying, 'TRADE'::character varying, 'PHYSICAL_STORE'::character varying, 'CHARACTER'::character varying, 'QUIZ_GAME'::character varying, 'QUIZ_TEST'::character varying, 'AI_EVENT'::character varying, 'CUMULATIVE_REPORT'::character varying])::text[])))
);


--
-- Name: autonomous_schedules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.autonomous_schedules (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    day_of_week smallint NOT NULL,
    local_start_time time without time zone NOT NULL,
    local_end_time time without time zone NOT NULL,
    time_zone character varying(60) DEFAULT 'Asia/Seoul'::character varying NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_autonomous_schedules_time CHECK ((((day_of_week >= 1) AND (day_of_week <= 7)) AND (local_end_time > local_start_time)))
);


--
-- Name: character_catalog; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.character_catalog (
    id uuid NOT NULL,
    character_code character varying(80) NOT NULL,
    name character varying(120) NOT NULL,
    source_title character varying(200) NOT NULL,
    source_url text NOT NULL,
    rights_status character varying(40) NOT NULL,
    rights_evidence_key character varying(512) NOT NULL,
    reviewed_at timestamp with time zone,
    status character varying(40) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: character_equipment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.character_equipment (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    character_profile_id uuid NOT NULL,
    inventory_item_id uuid NOT NULL,
    slot character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    equipped_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_character_equipment_slot CHECK (((slot)::text = ANY ((ARRAY['BACKGROUND'::character varying, 'EFFECT'::character varying, 'BACK'::character varying, 'BOTTOM'::character varying, 'SHOES'::character varying, 'TOP'::character varying, 'NECK'::character varying, 'FACE'::character varying, 'HAND'::character varying, 'HAT'::character varying])::text[]))),
    CONSTRAINT ck_character_equipment_version CHECK ((state_version >= 0))
);


--
-- Name: classroom_characters; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.classroom_characters (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    character_catalog_id uuid NOT NULL,
    store_product_id uuid,
    display_name character varying(120) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: classroom_modules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.classroom_modules (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    module_code character varying(40) NOT NULL,
    config_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    enabled_at timestamp with time zone,
    stopped_at timestamp with time zone,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_classroom_modules_canonical_code CHECK (((module_code)::text = ANY ((ARRAY['ROLE_SALARY'::character varying, 'INCOME_TAX'::character varying, 'STORE'::character varying, 'BUDGET'::character varying, 'PAYSLIP'::character varying, 'PUBLIC_FUND'::character varying, 'PUBLIC_BUDGET'::character varying, 'DEPOSIT'::character varying, 'SAVINGS'::character varying, 'CREDIT'::character varying, 'LOAN'::character varying, 'REAL_ESTATE'::character varying, 'HOUSING_SUBSCRIPTION'::character varying, 'INVESTMENT'::character varying, 'RISK_MANAGEMENT'::character varying, 'MARKET_PRICE'::character varying, 'TRADE'::character varying, 'PHYSICAL_STORE'::character varying, 'CHARACTER'::character varying, 'QUIZ_GAME'::character varying, 'QUIZ_TEST'::character varying, 'AI_EVENT'::character varying, 'CUMULATIVE_REPORT'::character varying])::text[]))),
    CONSTRAINT ck_classroom_modules_status CHECK (((status)::text = ANY ((ARRAY['DISABLED'::character varying, 'PENDING_CONFIGURATION'::character varying, 'ACTIVE'::character varying, 'STOPPING'::character varying])::text[])))
);


--
-- Name: classroom_policy_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.classroom_policy_versions (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    policy_area character varying(40) NOT NULL,
    version integer NOT NULL,
    "values" jsonb NOT NULL,
    schema_version character varying(20) NOT NULL,
    source character varying(40) NOT NULL,
    status character varying(40) NOT NULL,
    effective_from timestamp with time zone NOT NULL,
    superseded_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: classroom_students; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.classroom_students (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    salary_role_id uuid,
    roster_number integer NOT NULL,
    nickname character varying(40) NOT NULL,
    joined_at timestamp with time zone DEFAULT now() NOT NULL,
    retired_at timestamp with time zone,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: classroom_teachers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.classroom_teachers (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role character varying(40) NOT NULL,
    assigned_at timestamp with time zone DEFAULT now() NOT NULL,
    ended_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: classrooms; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.classrooms (
    id uuid NOT NULL,
    school_id uuid NOT NULL,
    course_template_version_id uuid,
    classroom_code character varying(8) NOT NULL,
    grade smallint NOT NULL,
    scale_label character varying(20) NOT NULL,
    actual_starts_on date,
    actual_ends_on date,
    app_mode_override character varying(40),
    time_zone character varying(60) DEFAULT 'Asia/Seoul'::character varying NOT NULL,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    currency_name character varying(10) NOT NULL,
    planned_student_count integer NOT NULL,
    display_name character varying(120) NOT NULL,
    class_number integer,
    description character varying(500),
    CONSTRAINT ck_classrooms_class_number CHECK (((class_number IS NULL) OR ((class_number >= 1) AND (class_number <= 99)))),
    CONSTRAINT ck_classrooms_code_format CHECK (((classroom_code)::text ~ '^[ABCDEFGHJKMNPQRSTUVWXYZ23456789]{8}$'::text)),
    CONSTRAINT ck_classrooms_currency_name CHECK ((((char_length((currency_name)::text) >= 1) AND (char_length((currency_name)::text) <= 10)) AND ((currency_name)::text ~ '^[가-힣A-Za-z0-9]+$'::text))),
    CONSTRAINT ck_classrooms_dates CHECK (((actual_ends_on IS NULL) OR (actual_starts_on IS NULL) OR (actual_ends_on >= actual_starts_on))),
    CONSTRAINT ck_classrooms_grade CHECK (((grade >= 4) AND (grade <= 6))),
    CONSTRAINT ck_classrooms_planned_student_count CHECK ((planned_student_count > 0)),
    CONSTRAINT ck_classrooms_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'CLOSING'::character varying, 'READ_ONLY'::character varying, 'ARCHIVED'::character varying])::text[])))
);


--
-- Name: course_template_modules; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.course_template_modules (
    id uuid NOT NULL,
    course_template_version_id uuid NOT NULL,
    module_code character varying(40) NOT NULL,
    recommendation_level character varying(40) NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    config_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_course_template_modules_canonical_code CHECK (((module_code)::text = ANY ((ARRAY['ROLE_SALARY'::character varying, 'INCOME_TAX'::character varying, 'STORE'::character varying, 'BUDGET'::character varying, 'PAYSLIP'::character varying, 'PUBLIC_FUND'::character varying, 'PUBLIC_BUDGET'::character varying, 'DEPOSIT'::character varying, 'SAVINGS'::character varying, 'CREDIT'::character varying, 'LOAN'::character varying, 'REAL_ESTATE'::character varying, 'HOUSING_SUBSCRIPTION'::character varying, 'INVESTMENT'::character varying, 'RISK_MANAGEMENT'::character varying, 'MARKET_PRICE'::character varying, 'TRADE'::character varying, 'PHYSICAL_STORE'::character varying, 'CHARACTER'::character varying, 'QUIZ_GAME'::character varying, 'QUIZ_TEST'::character varying, 'AI_EVENT'::character varying, 'CUMULATIVE_REPORT'::character varying])::text[])))
);


--
-- Name: course_template_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.course_template_versions (
    id uuid NOT NULL,
    course_template_id uuid NOT NULL,
    version integer NOT NULL,
    difficulty_profile jsonb DEFAULT '{}'::jsonb NOT NULL,
    status character varying(40) NOT NULL,
    published_at timestamp with time zone,
    retired_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: course_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.course_templates (
    id uuid NOT NULL,
    template_code character varying(40) NOT NULL,
    name character varying(120) NOT NULL,
    grade_min smallint NOT NULL,
    grade_max smallint NOT NULL,
    scale_label character varying(20) NOT NULL,
    status character varying(40) DEFAULT 'DRAFT'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_course_templates_grade_range CHECK ((((grade_min >= 4) AND (grade_min <= 6)) AND ((grade_max >= grade_min) AND (grade_max <= 6))))
);


--
-- Name: credit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.credit_events (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    event_type character varying(40) NOT NULL,
    score_delta integer NOT NULL,
    score_before integer NOT NULL,
    score_after integer NOT NULL,
    level_before character varying(40) NOT NULL,
    level_after character varying(40) NOT NULL,
    reason text NOT NULL,
    related_type character varying(40),
    related_id uuid,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_credit_events_score CHECK ((((score_before >= 0) AND (score_before <= 1000)) AND ((score_after >= 0) AND (score_after <= 1000)) AND ((score_before + score_delta) = score_after)))
);


--
-- Name: credit_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.credit_profiles (
    classroom_student_id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    level character varying(40) NOT NULL,
    score integer NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    evaluated_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_credit_profiles_level CHECK (((level)::text = ANY ((ARRAY['NORMAL'::character varying, 'GOOD'::character varying, 'CAUTION'::character varying, 'RESTRICTED'::character varying])::text[]))),
    CONSTRAINT ck_credit_profiles_score CHECK (((score >= 0) AND (score <= 1000)))
);


--
-- Name: deposit_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.deposit_accounts (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    product_version_id uuid NOT NULL,
    principal_amount bigint NOT NULL,
    expected_interest_amount bigint NOT NULL,
    opened_at timestamp with time zone DEFAULT now() NOT NULL,
    matures_at timestamp with time zone NOT NULL,
    closed_at timestamp with time zone,
    opening_transaction_group_id uuid NOT NULL,
    closing_transaction_group_id uuid,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_deposit_accounts_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'MATURED'::character varying, 'EARLY_TERMINATED'::character varying, 'CLOSE_SETTLED'::character varying])::text[]))),
    CONSTRAINT ck_deposit_accounts_values CHECK (((principal_amount > 0) AND (expected_interest_amount >= 0) AND (matures_at >= opened_at)))
);


--
-- Name: device_links; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.device_links (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    device_fingerprint_hash character varying(128) NOT NULL,
    device_name character varying(120),
    linked_at timestamp with time zone DEFAULT now() NOT NULL,
    last_seen_at timestamp with time zone,
    revoked_at timestamp with time zone,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: economic_event_drafts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.economic_event_drafts (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    ai_run_id uuid,
    activity_session_id uuid,
    title character varying(160) NOT NULL,
    content text NOT NULL,
    economic_effect jsonb NOT NULL,
    valid_from timestamp with time zone,
    valid_until timestamp with time zone,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    source_type character varying(40) NOT NULL,
    source_proposal_id uuid,
    investment_price_step_id uuid,
    revealed_at timestamp with time zone,
    CONSTRAINT ck_economic_event_drafts_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'APPROVED'::character varying, 'SCHEDULED'::character varying, 'APPLIED'::character varying, 'CANCELED'::character varying])::text[])))
);


--
-- Name: export_operations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.export_operations (
    async_operation_id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    requested_by_user_id uuid NOT NULL,
    export_type character varying(40) NOT NULL,
    filter_snapshot jsonb NOT NULL,
    result_s3_key character varying(512),
    retention_until timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_export_operations_retention CHECK ((retention_until >= created_at))
);


--
-- Name: financial_product_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.financial_product_versions (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    financial_product_id uuid NOT NULL,
    version integer NOT NULL,
    product_type character varying(40) NOT NULL,
    term_units integer NOT NULL,
    interest_rate_bps integer NOT NULL,
    early_termination_rate_bps integer NOT NULL,
    installment_count integer,
    installment_amount bigint,
    minimum_amount bigint NOT NULL,
    maximum_amount bigint NOT NULL,
    status character varying(40) NOT NULL,
    published_at timestamp with time zone NOT NULL,
    retired_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_financial_product_versions_values CHECK (((term_units > 0) AND ((interest_rate_bps >= 0) AND (interest_rate_bps <= 10000)) AND ((early_termination_rate_bps >= 0) AND (early_termination_rate_bps <= 10000)) AND ((installment_count IS NULL) OR (installment_count > 0)) AND ((installment_amount IS NULL) OR (installment_amount > 0)) AND (minimum_amount > 0) AND (maximum_amount >= minimum_amount)))
);


--
-- Name: financial_products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.financial_products (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    product_type character varying(40) NOT NULL,
    name character varying(120) NOT NULL,
    description text,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: idempotency_records; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.idempotency_records (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    actor_id uuid NOT NULL,
    route_key character varying(160) NOT NULL,
    idempotency_key character varying(128) NOT NULL,
    request_hash character varying(128) NOT NULL,
    response_status integer NOT NULL,
    response_body jsonb NOT NULL,
    transaction_group_id uuid,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: inventory_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_items (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    character_catalog_id uuid NOT NULL,
    source_order_item_id uuid,
    locked_trade_id uuid,
    acquired_at timestamp with time zone DEFAULT now() NOT NULL,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_inventory_items_status CHECK (((status)::text = ANY ((ARRAY['AVAILABLE'::character varying, 'LOCKED_TRADE'::character varying])::text[]))),
    CONSTRAINT ck_inventory_items_trade_lock CHECK ((((status)::text = 'LOCKED_TRADE'::text) = (locked_trade_id IS NOT NULL)))
);


--
-- Name: investment_assets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.investment_assets (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    scenario_version_id uuid NOT NULL,
    symbol character varying(20) NOT NULL,
    name character varying(120) NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    fictional_company_profile character varying(1000) DEFAULT '학급 투자 활동을 위해 만든 가상 기업입니다.'::character varying NOT NULL,
    min_return_bps integer DEFAULT 0 NOT NULL,
    max_return_bps integer DEFAULT 0 NOT NULL,
    CONSTRAINT ck_investment_assets_return_band CHECK ((min_return_bps <= max_return_bps))
);


--
-- Name: investment_markets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.investment_markets (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    active_scenario_version_id uuid,
    current_step integer DEFAULT 0 NOT NULL,
    opened_at timestamp with time zone,
    closed_at timestamp with time zone,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    investment_mode character varying(20) DEFAULT 'LONG_TERM'::character varying NOT NULL,
    CONSTRAINT ck_investment_markets_mode CHECK (((investment_mode)::text = ANY ((ARRAY['SHORT_TERM'::character varying, 'LONG_TERM'::character varying])::text[]))),
    CONSTRAINT ck_investment_markets_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'OPEN'::character varying, 'PAUSED'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: investment_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.investment_orders (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    investment_asset_id uuid NOT NULL,
    side character varying(10) NOT NULL,
    quantity integer NOT NULL,
    executed_price bigint,
    executed_price_step integer,
    transaction_group_id uuid,
    submitted_at timestamp with time zone DEFAULT now() NOT NULL,
    executed_at timestamp with time zone,
    reject_reason character varying(80),
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_investment_orders_status CHECK (((status)::text = ANY ((ARRAY['RECEIVED'::character varying, 'VALIDATING'::character varying, 'EXECUTED'::character varying, 'REJECTED'::character varying])::text[]))),
    CONSTRAINT ck_investment_orders_values CHECK ((((side)::text = ANY ((ARRAY['BUY'::character varying, 'SELL'::character varying])::text[])) AND (quantity > 0) AND ((executed_price IS NULL) OR (executed_price > 0)) AND ((executed_price_step IS NULL) OR (executed_price_step >= 0))))
);


--
-- Name: investment_positions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.investment_positions (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    investment_asset_id uuid NOT NULL,
    quantity integer DEFAULT 0 NOT NULL,
    average_price bigint DEFAULT 0 NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_investment_positions_values CHECK (((quantity >= 0) AND (average_price >= 0)))
);


--
-- Name: investment_price_steps; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.investment_price_steps (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    investment_asset_id uuid NOT NULL,
    step_number integer NOT NULL,
    price bigint NOT NULL,
    change_rate_bps integer NOT NULL,
    revealed_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_investment_price_steps_values CHECK (((step_number >= 0) AND (price > 0) AND ((change_rate_bps >= '-10000'::integer) AND (change_rate_bps <= 100000))))
);


--
-- Name: investment_scenario_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.investment_scenario_versions (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    investment_market_id uuid NOT NULL,
    version integer NOT NULL,
    name character varying(120) NOT NULL,
    scenario_seed character varying(128),
    status character varying(40) NOT NULL,
    published_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    investment_mode character varying(20) DEFAULT 'LONG_TERM'::character varying NOT NULL,
    scenario_source character varying(30) DEFAULT 'AI_GENERATED'::character varying NOT NULL,
    reference_series_code character varying(40),
    reference_event_name character varying(120),
    reference_source_name character varying(120),
    reference_source_url character varying(500),
    reference_period_start date,
    reference_period_end date,
    CONSTRAINT ck_investment_historical_reference CHECK (((((scenario_source)::text = 'HISTORICAL_INDEX'::text) AND ((investment_mode)::text = 'LONG_TERM'::text) AND (reference_series_code IS NOT NULL) AND (reference_event_name IS NOT NULL) AND (reference_source_name IS NOT NULL) AND (reference_source_url IS NOT NULL) AND (reference_period_start IS NOT NULL) AND (reference_period_end IS NOT NULL) AND (reference_period_start < reference_period_end)) OR (((scenario_source)::text = 'AI_GENERATED'::text) AND (reference_series_code IS NULL) AND (reference_event_name IS NULL) AND (reference_source_name IS NULL) AND (reference_source_url IS NULL) AND (reference_period_start IS NULL) AND (reference_period_end IS NULL)))),
    CONSTRAINT ck_investment_scenario_mode CHECK (((investment_mode)::text = ANY ((ARRAY['SHORT_TERM'::character varying, 'LONG_TERM'::character varying])::text[]))),
    CONSTRAINT ck_investment_scenario_source CHECK (((scenario_source)::text = ANY ((ARRAY['AI_GENERATED'::character varying, 'HISTORICAL_INDEX'::character varying])::text[])))
);


--
-- Name: ledger_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ledger_accounts (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    owner_type character varying(40) NOT NULL,
    classroom_student_id uuid,
    system_owner_key character varying(80),
    account_type character varying(40) NOT NULL,
    current_balance bigint DEFAULT 0 NOT NULL,
    next_entry_sequence bigint DEFAULT 1 NOT NULL,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_ledger_accounts_balance CHECK ((((account_type)::text <> 'STUDENT_WALLET'::text) OR (current_balance >= 0))),
    CONSTRAINT ck_ledger_accounts_owner CHECK (((((owner_type)::text = 'STUDENT'::text) AND (classroom_student_id IS NOT NULL) AND (system_owner_key IS NULL)) OR (((owner_type)::text = 'SYSTEM'::text) AND (classroom_student_id IS NULL) AND (system_owner_key IS NOT NULL)))),
    CONSTRAINT ck_ledger_accounts_sequence CHECK ((next_entry_sequence >= 1))
);


--
-- Name: ledger_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ledger_entries (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    transaction_group_id uuid NOT NULL,
    ledger_account_id uuid NOT NULL,
    account_sequence bigint NOT NULL,
    amount bigint NOT NULL,
    balance_before bigint NOT NULL,
    balance_after bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_ledger_entries_amount_balance CHECK (((amount <> 0) AND ((balance_before + amount) = balance_after) AND (account_sequence >= 1)))
);


--
-- Name: loan_applications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.loan_applications (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    loan_policy_version_id uuid NOT NULL,
    requested_amount bigint NOT NULL,
    approved_amount bigint,
    credit_level_snapshot character varying(40) NOT NULL,
    credit_score_snapshot integer NOT NULL,
    decision_reason text,
    decided_by_user_id uuid,
    decided_at timestamp with time zone,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_loan_applications_status CHECK (((status)::text = ANY ((ARRAY['APPLIED'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'CANCELED'::character varying])::text[]))),
    CONSTRAINT ck_loan_applications_values CHECK (((requested_amount > 0) AND ((approved_amount IS NULL) OR (approved_amount > 0)) AND ((credit_score_snapshot >= 0) AND (credit_score_snapshot <= 1000))))
);


--
-- Name: loan_installments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.loan_installments (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    loan_id uuid NOT NULL,
    installment_number integer NOT NULL,
    scheduled_principal bigint NOT NULL,
    scheduled_interest bigint NOT NULL,
    due_at timestamp with time zone NOT NULL,
    grace_until timestamp with time zone NOT NULL,
    paid_principal bigint DEFAULT 0 NOT NULL,
    paid_interest bigint DEFAULT 0 NOT NULL,
    paid_at timestamp with time zone,
    transaction_group_id uuid,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_loan_installments_status CHECK (((status)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'MISSED'::character varying, 'PAID'::character varying])::text[]))),
    CONSTRAINT ck_loan_installments_values CHECK (((installment_number > 0) AND (scheduled_principal >= 0) AND (scheduled_interest >= 0) AND ((paid_principal >= 0) AND (paid_principal <= scheduled_principal)) AND ((paid_interest >= 0) AND (paid_interest <= scheduled_interest)) AND (grace_until >= due_at)))
);


--
-- Name: loan_policy_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.loan_policy_versions (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    version integer NOT NULL,
    maximum_principal bigint NOT NULL,
    interest_rate_bps integer NOT NULL,
    term_units integer NOT NULL,
    installment_count integer NOT NULL,
    grace_units integer DEFAULT 0 NOT NULL,
    credit_adjustments jsonb DEFAULT '{}'::jsonb NOT NULL,
    status character varying(40) NOT NULL,
    effective_from timestamp with time zone NOT NULL,
    superseded_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_loan_policy_versions_values CHECK (((maximum_principal > 0) AND ((interest_rate_bps >= 0) AND (interest_rate_bps <= 10000)) AND (term_units > 0) AND (installment_count > 0) AND (grace_units >= 0)))
);


--
-- Name: loans; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.loans (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    loan_application_id uuid NOT NULL,
    principal_amount bigint NOT NULL,
    applied_interest_rate_bps integer NOT NULL,
    credit_level_at_approval character varying(40) NOT NULL,
    outstanding_principal bigint NOT NULL,
    outstanding_interest bigint DEFAULT 0 NOT NULL,
    disbursement_transaction_group_id uuid NOT NULL,
    started_at timestamp with time zone NOT NULL,
    matures_at timestamp with time zone NOT NULL,
    closed_at timestamp with time zone,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_loans_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'OVERDUE'::character varying, 'REPAID'::character varying, 'CLOSED_UNPAID'::character varying, 'FAILED'::character varying])::text[]))),
    CONSTRAINT ck_loans_values CHECK (((principal_amount > 0) AND ((applied_interest_rate_bps >= 0) AND (applied_interest_rate_bps <= 10000)) AND ((outstanding_principal >= 0) AND (outstanding_principal <= principal_amount)) AND (outstanding_interest >= 0) AND (matures_at >= started_at)))
);


--
-- Name: outbox_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.outbox_events (
    event_id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    realtime_stream_id uuid NOT NULL,
    channel_sequence bigint NOT NULL,
    event_type character varying(120) NOT NULL,
    aggregate_type character varying(40) NOT NULL,
    aggregate_id uuid NOT NULL,
    state_version bigint NOT NULL,
    payload jsonb NOT NULL,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    published_at timestamp with time zone,
    publish_attempts integer DEFAULT 0 NOT NULL,
    last_error text,
    CONSTRAINT ck_outbox_events_sequence CHECK (((channel_sequence >= 1) AND (state_version >= 0) AND (publish_attempts >= 0)))
);


--
-- Name: payslips; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payslips (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    salary_role_id uuid,
    period_key character varying(40) NOT NULL,
    gross_amount bigint NOT NULL,
    deduction_amount bigint NOT NULL,
    tax_amount bigint NOT NULL,
    net_amount bigint NOT NULL,
    policy_snapshot_id uuid NOT NULL,
    transaction_group_id uuid NOT NULL,
    issued_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_payslips_amounts CHECK (((gross_amount >= 0) AND (deduction_amount >= 0) AND (tax_amount >= 0) AND (net_amount >= 0) AND (gross_amount = ((deduction_amount + tax_amount) + net_amount))))
);


--
-- Name: policy_snapshot_sources; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.policy_snapshot_sources (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    policy_snapshot_id uuid NOT NULL,
    source_type character varying(40) NOT NULL,
    source_id uuid NOT NULL,
    source_version integer NOT NULL,
    source_hash character varying(128) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: policy_snapshots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.policy_snapshots (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    snapshot_hash character varying(128) NOT NULL,
    schema_version character varying(20) NOT NULL,
    calculated_values jsonb NOT NULL,
    rounding_rule character varying(40) NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: properties; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.properties (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    coordinate character varying(12) NOT NULL,
    display_name character varying(120) NOT NULL,
    row_index integer NOT NULL,
    column_index integer NOT NULL,
    property_tier character varying(20) DEFAULT 'GENERAL'::character varying NOT NULL,
    is_public boolean DEFAULT true NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    allows_rent boolean DEFAULT true NOT NULL,
    allows_jeonse boolean DEFAULT true NOT NULL,
    allows_sale boolean DEFAULT true NOT NULL,
    allows_subscription boolean DEFAULT false NOT NULL,
    sale_price bigint DEFAULT 0 NOT NULL,
    deposit_amount bigint DEFAULT 0 NOT NULL,
    monthly_rent bigint DEFAULT 0 NOT NULL,
    rent_payment_cycle_days integer DEFAULT 7 NOT NULL,
    contract_term_days integer DEFAULT 28 NOT NULL,
    owner_kind character varying(20) DEFAULT 'TEACHER'::character varying NOT NULL,
    owner_student_id uuid,
    subscription_offer_id uuid,
    subscription_winner_student_id uuid,
    status character varying(40) DEFAULT 'AVAILABLE'::character varying NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    layout_generation bigint DEFAULT 1 NOT NULL,
    rent_deposit_amount bigint DEFAULT 0 NOT NULL,
    CONSTRAINT ck_properties_rent_deposit_amount CHECK ((rent_deposit_amount >= 0)),
    CONSTRAINT ck_properties_values CHECK (((row_index >= 1) AND (column_index >= 1) AND (sale_price >= 0) AND (deposit_amount >= 0) AND (monthly_rent >= 0) AND ((rent_payment_cycle_days >= 1) AND (rent_payment_cycle_days <= 365)) AND ((contract_term_days >= 1) AND (contract_term_days <= 365)) AND ((property_tier)::text = ANY ((ARRAY['GENERAL'::character varying, 'POPULAR'::character varying])::text[])) AND ((owner_kind)::text = ANY ((ARRAY['TEACHER'::character varying, 'STUDENT'::character varying])::text[])) AND ((status)::text = ANY ((ARRAY['TEACHER_OWNED'::character varying, 'AVAILABLE'::character varying, 'RENTED'::character varying, 'STUDENT_OWNED'::character varying, 'LISTED_FOR_RENT'::character varying, 'LISTED_FOR_SALE'::character varying, 'SUBSCRIPTION_OPEN'::character varying, 'INACTIVE'::character varying])::text[])) AND ((((owner_kind)::text = 'TEACHER'::text) AND (owner_student_id IS NULL)) OR (((owner_kind)::text = 'STUDENT'::text) AND (owner_student_id IS NOT NULL)))))
);


--
-- Name: property_contract_payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.property_contract_payments (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    property_contract_id uuid NOT NULL,
    due_at timestamp with time zone NOT NULL,
    amount bigint NOT NULL,
    paid_at timestamp with time zone,
    transaction_group_id uuid,
    status character varying(20) DEFAULT 'SCHEDULED'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_property_contract_payments_values CHECK (((amount > 0) AND ((status)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'PAID'::character varying, 'MISSED'::character varying])::text[]))))
);


--
-- Name: property_contracts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.property_contracts (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    property_id uuid NOT NULL,
    lease_listing_id uuid,
    subscription_offer_id uuid,
    subscription_application_id uuid,
    contract_type character varying(20) NOT NULL,
    occupant_student_id uuid NOT NULL,
    landlord_kind character varying(20) NOT NULL,
    landlord_student_id uuid,
    deposit_amount bigint DEFAULT 0 NOT NULL,
    monthly_rent bigint DEFAULT 0 NOT NULL,
    sale_price bigint DEFAULT 0 NOT NULL,
    payment_cycle_days integer,
    contract_term_days integer,
    started_at timestamp with time zone,
    ends_at timestamp with time zone,
    next_payment_at timestamp with time zone,
    approved_by_user_id uuid,
    decision_reason text,
    approved_at timestamp with time zone,
    terminated_at timestamp with time zone,
    status character varying(30) DEFAULT 'PENDING'::character varying NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    paid_at timestamp with time zone,
    CONSTRAINT ck_property_contracts_values CHECK ((((contract_type)::text = ANY ((ARRAY['RENT'::character varying, 'JEONSE'::character varying, 'SALE'::character varying])::text[])) AND ((landlord_kind)::text = ANY ((ARRAY['TEACHER'::character varying, 'STUDENT'::character varying])::text[])) AND (deposit_amount >= 0) AND (monthly_rent >= 0) AND (sale_price >= 0) AND ((payment_cycle_days IS NULL) OR ((payment_cycle_days >= 1) AND (payment_cycle_days <= 365))) AND ((contract_term_days IS NULL) OR ((contract_term_days >= 1) AND (contract_term_days <= 365))) AND ((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACTIVE'::character varying, 'OVERDUE'::character varying, 'COMPLETED'::character varying, 'REJECTED'::character varying, 'TERMINATED'::character varying])::text[])) AND ((((landlord_kind)::text = 'TEACHER'::text) AND (landlord_student_id IS NULL)) OR (((landlord_kind)::text = 'STUDENT'::text) AND (landlord_student_id IS NOT NULL)))))
);


--
-- Name: property_layouts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.property_layouts (
    classroom_id uuid NOT NULL,
    row_count integer NOT NULL,
    column_count integer NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    layout_generation bigint DEFAULT 1 NOT NULL,
    CONSTRAINT ck_property_layout_dimensions CHECK ((((row_count >= 1) AND (row_count <= 26)) AND ((column_count >= 1) AND (column_count <= 26))))
);


--
-- Name: property_lease_listings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.property_lease_listings (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    property_id uuid NOT NULL,
    landlord_student_id uuid NOT NULL,
    lease_type character varying(20) NOT NULL,
    deposit_amount bigint DEFAULT 0 NOT NULL,
    monthly_rent bigint DEFAULT 0 NOT NULL,
    payment_cycle_days integer DEFAULT 7 NOT NULL,
    contract_term_days integer DEFAULT 28 NOT NULL,
    is_public boolean DEFAULT true NOT NULL,
    status character varying(30) DEFAULT 'ACTIVE'::character varying NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_property_lease_listings_values CHECK ((((lease_type)::text = ANY ((ARRAY['RENT'::character varying, 'JEONSE'::character varying])::text[])) AND (deposit_amount >= 0) AND (monthly_rent >= 0) AND ((payment_cycle_days >= 1) AND (payment_cycle_days <= 365)) AND ((contract_term_days >= 1) AND (contract_term_days <= 365)) AND ((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'CLOSED'::character varying])::text[]))))
);


--
-- Name: property_sale_listings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.property_sale_listings (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    property_id uuid NOT NULL,
    seller_student_id uuid NOT NULL,
    sale_price bigint NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_property_sale_listings_values CHECK (((sale_price > 0) AND ((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'CLOSED'::character varying, 'PURCHASED'::character varying])::text[]))))
);


--
-- Name: public_classroom_fund_balances; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.public_classroom_fund_balances AS
 SELECT classroom_id,
    current_balance
   FROM public.ledger_accounts
  WHERE (((account_type)::text = 'CLASSROOM_FUND'::text) AND ((status)::text = 'ACTIVE'::text));


--
-- Name: public_investment_prices; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.public_investment_prices AS
 SELECT asset.classroom_id,
    asset.symbol,
    asset.name,
    step.step_number,
    step.price,
    step.change_rate_bps,
    step.revealed_at
   FROM (public.investment_assets asset
     JOIN public.investment_price_steps step ON (((step.classroom_id = asset.classroom_id) AND (step.investment_asset_id = asset.id))))
  WHERE (step.revealed_at IS NOT NULL);


--
-- Name: quiz_result_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_result_versions (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    quiz_session_id uuid NOT NULL,
    version integer NOT NULL,
    status character varying(40) NOT NULL,
    reward_transaction_group_id uuid,
    published_at timestamp with time zone,
    supersedes_result_version_id uuid,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: quiz_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_sessions (
    activity_session_id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    mode character varying(20) NOT NULL,
    content_type character varying(40) NOT NULL,
    reward_policy_public jsonb DEFAULT '{}'::jsonb NOT NULL,
    leaderboard_public boolean DEFAULT false NOT NULL,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_quiz_sessions_status CHECK (((status)::text = ANY ((ARRAY['READY'::character varying, 'OPEN'::character varying, 'SCORING'::character varying, 'REVIEWING'::character varying, 'RESULT_PUBLISHED'::character varying, 'CLOSED'::character varying, 'CANCELED'::character varying])::text[])))
);


--
-- Name: quiz_student_results; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_student_results (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    quiz_result_version_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    score integer NOT NULL,
    correct_count integer NOT NULL,
    rank integer,
    reward_amount bigint DEFAULT 0 NOT NULL,
    total_response_time_ms bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_quiz_student_results_values CHECK (((score >= 0) AND (correct_count >= 0) AND (reward_amount >= 0) AND ((rank IS NULL) OR (rank >= 1)) AND ((total_response_time_ms IS NULL) OR (total_response_time_ms >= 0))))
);


--
-- Name: student_character_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_character_profiles (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    equipment_revision bigint DEFAULT 0 NOT NULL,
    equipment_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_student_character_profiles_version CHECK (((state_version >= 0) AND (equipment_revision >= 0)))
);


--
-- Name: public_quiz_leaderboard; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.public_quiz_leaderboard AS
 SELECT result.classroom_id,
    result.quiz_session_id,
    student_result.rank,
    student.nickname,
    equipped.character_code,
    equipped.character_name
   FROM ((((public.quiz_result_versions result
     JOIN public.quiz_sessions session ON (((session.classroom_id = result.classroom_id) AND (session.activity_session_id = result.quiz_session_id))))
     JOIN public.quiz_student_results student_result ON (((student_result.classroom_id = result.classroom_id) AND (student_result.quiz_result_version_id = result.id))))
     JOIN public.classroom_students student ON (((student.classroom_id = student_result.classroom_id) AND (student.id = student_result.classroom_student_id))))
     LEFT JOIN LATERAL ( SELECT catalog.character_code,
            catalog.name AS character_name
           FROM (((public.student_character_profiles profile
             JOIN public.character_equipment equipment ON (((equipment.classroom_id = profile.classroom_id) AND (equipment.classroom_student_id = profile.classroom_student_id) AND (equipment.character_profile_id = profile.id))))
             JOIN public.inventory_items item ON (((item.classroom_id = equipment.classroom_id) AND (item.classroom_student_id = equipment.classroom_student_id) AND (item.id = equipment.inventory_item_id))))
             JOIN public.character_catalog catalog ON ((catalog.id = item.character_catalog_id)))
          WHERE ((profile.classroom_id = student.classroom_id) AND (profile.classroom_student_id = student.id))
          ORDER BY equipment.equipped_at DESC, equipment.id
         LIMIT 1) equipped ON (true))
  WHERE (((session.mode)::text = 'GAME'::text) AND (session.leaderboard_public = true) AND (result.published_at IS NOT NULL) AND ((student_result.rank >= 1) AND (student_result.rank <= 3)));


--
-- Name: vote_ballots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vote_ballots (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    vote_id uuid NOT NULL,
    vote_option_id uuid NOT NULL,
    eligibility_token_hash character varying(128) NOT NULL,
    receipt_hash character varying(128) NOT NULL,
    cast_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: vote_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vote_options (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    vote_id uuid NOT NULL,
    label character varying(160) NOT NULL,
    description text,
    sort_order integer NOT NULL,
    budget_amount bigint,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_vote_options_values CHECK (((sort_order >= 0) AND ((budget_amount IS NULL) OR (budget_amount >= 0))))
);


--
-- Name: public_vote_totals; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.public_vote_totals AS
 SELECT option.classroom_id,
    option.vote_id,
    option.id AS vote_option_id,
    option.label,
    count(ballot.id) AS ballot_count
   FROM (public.vote_options option
     LEFT JOIN public.vote_ballots ballot ON (((ballot.classroom_id = option.classroom_id) AND (ballot.vote_id = option.vote_id) AND (ballot.vote_option_id = option.id))))
  GROUP BY option.classroom_id, option.vote_id, option.id, option.label;


--
-- Name: quiz_answers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_answers (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    quiz_submission_id uuid NOT NULL,
    quiz_session_question_id uuid NOT NULL,
    selected_option_key character varying(20),
    free_text text,
    is_correct boolean,
    score_awarded integer,
    response_time_ms integer,
    answered_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_quiz_answers_values CHECK (((NOT ((selected_option_key IS NOT NULL) AND (free_text IS NOT NULL))) AND ((score_awarded IS NULL) OR (score_awarded >= 0)) AND ((response_time_ms IS NULL) OR (response_time_ms >= 0))))
);


--
-- Name: quiz_options; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_options (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    quiz_question_version_id uuid NOT NULL,
    option_key character varying(20) NOT NULL,
    option_text text NOT NULL,
    is_correct boolean NOT NULL,
    sort_order integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: quiz_question_issues; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_question_issues (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    quiz_session_question_id uuid NOT NULL,
    reported_by_student_id uuid,
    reason text NOT NULL,
    resolution text,
    resolved_by_user_id uuid,
    resolved_at timestamp with time zone,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_quiz_question_issues_status CHECK (((status)::text = ANY ((ARRAY['VALID'::character varying, 'UNDER_REVIEW'::character varying, 'INVALIDATED'::character varying])::text[])))
);


--
-- Name: quiz_question_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_question_versions (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    quiz_question_id uuid NOT NULL,
    version integer NOT NULL,
    content_type character varying(40) NOT NULL,
    question_type character varying(40) NOT NULL,
    status character varying(40) NOT NULL,
    prompt text NOT NULL,
    explanation text,
    source character varying(40) NOT NULL,
    ai_run_id uuid,
    published_at timestamp with time zone,
    retired_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_quiz_question_versions_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'REVIEW_REQUIRED'::character varying, 'APPROVED'::character varying, 'PUBLISHED'::character varying, 'RETIRED'::character varying])::text[])))
);


--
-- Name: quiz_questions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_questions (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    created_by_user_id uuid NOT NULL,
    content_type character varying(40) NOT NULL,
    title character varying(160),
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: quiz_session_questions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_session_questions (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    quiz_session_id uuid NOT NULL,
    quiz_question_version_id uuid NOT NULL,
    sequence_number integer NOT NULL,
    prompt_snapshot text NOT NULL,
    options_snapshot jsonb NOT NULL,
    correct_answer_snapshot jsonb NOT NULL,
    score_value integer NOT NULL,
    time_limit_seconds integer,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_quiz_session_questions_values CHECK (((sequence_number > 0) AND (score_value >= 0) AND ((time_limit_seconds IS NULL) OR (time_limit_seconds > 0))))
);


--
-- Name: quiz_submissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quiz_submissions (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    quiz_session_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    mode character varying(20) NOT NULL,
    answer_sheet_version integer NOT NULL,
    submitted_at timestamp with time zone,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: realtime_streams; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.realtime_streams (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    stream_type character varying(40) NOT NULL,
    resource_id uuid,
    next_sequence bigint DEFAULT 1 NOT NULL,
    retention_from_sequence bigint DEFAULT 1 NOT NULL,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_realtime_streams_sequences CHECK (((next_sequence >= 1) AND (retention_from_sequence >= 1)))
);


--
-- Name: regrading_runs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.regrading_runs (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    quiz_session_id uuid NOT NULL,
    source_result_version_id uuid NOT NULL,
    new_result_version_id uuid,
    reason text NOT NULL,
    started_by_user_id uuid NOT NULL,
    started_at timestamp with time zone DEFAULT now() NOT NULL,
    completed_at timestamp with time zone,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_regrading_runs_status CHECK (((status)::text = ANY ((ARRAY['REQUESTED'::character varying, 'RUNNING'::character varying, 'COMPLETED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: report_snapshots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.report_snapshots (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid,
    report_type character varying(40) NOT NULL,
    schema_version character varying(20) NOT NULL,
    snapshot jsonb NOT NULL,
    as_of timestamp with time zone NOT NULL,
    retention_until timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_report_snapshots_retention CHECK (((retention_until >= as_of) AND (retention_until >= created_at)))
);


--
-- Name: salary_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.salary_roles (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    name character varying(80) NOT NULL,
    gross_amount bigint NOT NULL,
    deductions_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    role_code character varying(40) NOT NULL,
    CONSTRAINT ck_salary_roles_amount CHECK ((gross_amount >= 0)),
    CONSTRAINT ck_salary_roles_role_code CHECK (((role_code)::text ~ '^[A-Z][A-Z0-9_]{0,39}$'::text))
);


--
-- Name: savings_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.savings_accounts (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    product_version_id uuid NOT NULL,
    installment_amount bigint NOT NULL,
    installment_count integer NOT NULL,
    paid_installment_count integer DEFAULT 0 NOT NULL,
    principal_balance bigint DEFAULT 0 NOT NULL,
    expected_interest_amount bigint NOT NULL,
    opened_at timestamp with time zone DEFAULT now() NOT NULL,
    matures_at timestamp with time zone NOT NULL,
    closed_at timestamp with time zone,
    opening_transaction_group_id uuid,
    closing_transaction_group_id uuid,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_savings_accounts_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'MATURED'::character varying, 'EARLY_TERMINATED'::character varying, 'CLOSE_SETTLED'::character varying])::text[]))),
    CONSTRAINT ck_savings_accounts_values CHECK (((installment_amount > 0) AND (installment_count > 0) AND ((paid_installment_count >= 0) AND (paid_installment_count <= installment_count)) AND (principal_balance >= 0) AND (expected_interest_amount >= 0) AND (matures_at >= opened_at)))
);


--
-- Name: savings_installments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.savings_installments (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    savings_account_id uuid NOT NULL,
    installment_number integer NOT NULL,
    scheduled_amount bigint NOT NULL,
    due_at timestamp with time zone NOT NULL,
    retryable_until timestamp with time zone NOT NULL,
    paid_at timestamp with time zone,
    transaction_group_id uuid,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_savings_installments_status CHECK (((status)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'PAID'::character varying, 'MISSED'::character varying])::text[]))),
    CONSTRAINT ck_savings_installments_values CHECK (((installment_number > 0) AND (scheduled_amount > 0) AND (retryable_until >= due_at)))
);


--
-- Name: school_ai_setting_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.school_ai_setting_versions (
    id uuid NOT NULL,
    school_id uuid NOT NULL,
    version integer NOT NULL,
    enabled boolean NOT NULL,
    reason text NOT NULL,
    effective_from timestamp with time zone NOT NULL,
    superseded_at timestamp with time zone,
    created_by_user_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_school_ai_setting_versions_validity CHECK (((version > 0) AND (btrim(reason) <> ''::text) AND ((superseded_at IS NULL) OR (superseded_at > effective_from))))
);


--
-- Name: school_memberships; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.school_memberships (
    id uuid NOT NULL,
    school_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role character varying(40) NOT NULL,
    status character varying(40) DEFAULT 'ACTIVE'::character varying NOT NULL,
    assigned_at timestamp with time zone DEFAULT now() NOT NULL,
    ended_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: schools; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.schools (
    id uuid NOT NULL,
    school_code character varying(40) NOT NULL,
    name character varying(120) NOT NULL,
    status character varying(40) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_schools_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: state_transitions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.state_transitions (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    aggregate_type character varying(40) NOT NULL,
    aggregate_id uuid NOT NULL,
    previous_status character varying(40),
    next_status character varying(40) NOT NULL,
    command character varying(80) NOT NULL,
    actor_type character varying(40) NOT NULL,
    actor_id uuid,
    state_version bigint NOT NULL,
    reason_code character varying(80),
    reason text,
    transaction_group_id uuid,
    occurred_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: store_order_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_order_items (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    store_order_id uuid NOT NULL,
    store_product_version_id uuid NOT NULL,
    quantity integer NOT NULL,
    unit_price bigint NOT NULL,
    vat_rate_bps integer NOT NULL,
    subtotal_amount bigint NOT NULL,
    tax_amount bigint NOT NULL,
    total_amount bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_store_order_items_values CHECK (((quantity > 0) AND (unit_price >= 0) AND ((vat_rate_bps >= 0) AND (vat_rate_bps <= 10000)) AND (subtotal_amount = (unit_price * quantity)) AND (tax_amount >= 0) AND (total_amount = (subtotal_amount + tax_amount))))
);


--
-- Name: store_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_orders (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    subtotal_amount bigint NOT NULL,
    tax_amount bigint NOT NULL,
    total_amount bigint NOT NULL,
    policy_snapshot_id uuid NOT NULL,
    transaction_group_id uuid,
    received_at timestamp with time zone,
    operation_canceled_at timestamp with time zone,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_store_orders_amounts CHECK (((subtotal_amount >= 0) AND (tax_amount >= 0) AND (total_amount >= 0) AND ((subtotal_amount + tax_amount) = total_amount))),
    CONSTRAINT ck_store_orders_status CHECK (((status)::text = ANY ((ARRAY['REQUESTED'::character varying, 'COMPLETED'::character varying, 'PAID'::character varying, 'FAILED'::character varying, 'READY_FOR_PICKUP'::character varying, 'RECEIVED'::character varying, 'OPERATION_CANCELED'::character varying])::text[])))
);


--
-- Name: store_product_versions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_product_versions (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    store_product_id uuid NOT NULL,
    version integer NOT NULL,
    product_kind character varying(40) NOT NULL,
    unit_price bigint NOT NULL,
    vat_rate_bps integer NOT NULL,
    purchasable boolean DEFAULT true NOT NULL,
    metadata jsonb DEFAULT '{}'::jsonb NOT NULL,
    published_at timestamp with time zone NOT NULL,
    retired_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_store_product_versions_values CHECK (((unit_price >= 0) AND ((vat_rate_bps >= 0) AND (vat_rate_bps <= 10000))))
);


--
-- Name: store_products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_products (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    name character varying(120) NOT NULL,
    description text,
    product_kind character varying(40) NOT NULL,
    created_by_user_id uuid NOT NULL,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    image_url text
);


--
-- Name: store_stocks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_stocks (
    store_product_id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    available_quantity integer DEFAULT 0 NOT NULL,
    reserved_quantity integer DEFAULT 0 NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_store_stocks_quantities CHECK (((available_quantity >= 0) AND (reserved_quantity >= 0)))
);


--
-- Name: student_credentials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_credentials (
    classroom_student_id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    password_hash text NOT NULL,
    credential_version bigint DEFAULT 1 NOT NULL,
    changed_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: student_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_sessions (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    device_link_id uuid,
    token_hash character varying(128) NOT NULL,
    status character varying(40) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: subscription_account_deposits; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscription_account_deposits (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    amount bigint NOT NULL,
    deposited_at timestamp with time zone NOT NULL,
    deposit_type character varying(20) NOT NULL,
    transaction_group_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_subscription_account_deposits_values CHECK (((amount > 0) AND ((deposit_type)::text = ANY ((ARRAY['MANUAL'::character varying, 'AUTO'::character varying, 'REFUND'::character varying])::text[]))))
);


--
-- Name: subscription_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscription_accounts (
    classroom_student_id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    total_deposit_amount bigint DEFAULT 0 NOT NULL,
    deposit_count integer DEFAULT 0 NOT NULL,
    last_deposited_at timestamp with time zone,
    auto_deposit_amount bigint,
    auto_deposit_cycle_days integer,
    next_auto_deposit_at timestamp with time zone,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_subscription_accounts_values CHECK (((total_deposit_amount >= 0) AND (deposit_count >= 0) AND ((auto_deposit_amount IS NULL) OR (auto_deposit_amount > 0)) AND ((auto_deposit_cycle_days IS NULL) OR ((auto_deposit_cycle_days >= 1) AND (auto_deposit_cycle_days <= 365))) AND ((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'CLOSED'::character varying])::text[]))))
);


--
-- Name: subscription_applications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscription_applications (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    subscription_offer_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    deposit_count_snapshot integer NOT NULL,
    deposit_amount_snapshot bigint NOT NULL,
    assigned_property_id uuid,
    status character varying(20) DEFAULT 'APPLIED'::character varying NOT NULL,
    applied_at timestamp with time zone NOT NULL,
    drawn_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_subscription_applications_values CHECK (((deposit_count_snapshot >= 0) AND (deposit_amount_snapshot >= 0) AND ((status)::text = ANY ((ARRAY['APPLIED'::character varying, 'WON'::character varying, 'LOST'::character varying, 'PURCHASED'::character varying, 'CANCELED'::character varying])::text[]))))
);


--
-- Name: subscription_auto_deposit_policies; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscription_auto_deposit_policies (
    classroom_id uuid NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    amount bigint,
    cycle_days integer,
    state_version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_subscription_auto_deposit_policies_values CHECK ((((enabled = false) AND (amount IS NULL) AND (cycle_days IS NULL)) OR ((enabled = true) AND (amount > 0) AND ((cycle_days >= 1) AND (cycle_days <= 365)))))
);


--
-- Name: subscription_offer_properties; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscription_offer_properties (
    classroom_id uuid NOT NULL,
    subscription_offer_id uuid NOT NULL,
    property_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: subscription_offers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.subscription_offers (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    title character varying(120) NOT NULL,
    application_opens_at timestamp with time zone NOT NULL,
    application_closes_at timestamp with time zone NOT NULL,
    sale_price bigint NOT NULL,
    supply_quantity integer NOT NULL,
    minimum_deposit_count integer DEFAULT 0 NOT NULL,
    minimum_balance bigint DEFAULT 0 NOT NULL,
    draw_method character varying(20) DEFAULT 'RANDOM'::character varying NOT NULL,
    status character varying(30) DEFAULT 'OPEN'::character varying NOT NULL,
    drawn_at timestamp with time zone,
    drawn_by_user_id uuid,
    state_version bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_subscription_offers_values CHECK (((application_closes_at > application_opens_at) AND (sale_price > 0) AND (supply_quantity > 0) AND (minimum_deposit_count >= 0) AND (minimum_balance >= 0) AND ((draw_method)::text = 'RANDOM'::text) AND ((status)::text = ANY ((ARRAY['OPEN'::character varying, 'CLOSED'::character varying, 'DRAWN'::character varying, 'CANCELED'::character varying])::text[]))))
);


--
-- Name: teacher_credentials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teacher_credentials (
    teacher_user_id uuid NOT NULL,
    email character varying(254) NOT NULL,
    password_hash text NOT NULL,
    credential_version bigint DEFAULT 1 NOT NULL,
    changed_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: trade_decisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.trade_decisions (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    trade_id uuid NOT NULL,
    decision_type character varying(40) NOT NULL,
    actor_type character varying(40) NOT NULL,
    actor_id uuid NOT NULL,
    decision character varying(40) NOT NULL,
    reason text,
    decided_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: trade_item_legs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.trade_item_legs (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    trade_id uuid NOT NULL,
    inventory_item_id uuid NOT NULL,
    from_student_id uuid NOT NULL,
    to_student_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_trade_item_legs_students CHECK ((from_student_id <> to_student_id))
);


--
-- Name: trade_money_legs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.trade_money_legs (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    trade_id uuid NOT NULL,
    from_student_id uuid NOT NULL,
    to_student_id uuid NOT NULL,
    amount bigint NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_trade_money_legs_values CHECK (((amount > 0) AND (from_student_id <> to_student_id)))
);


--
-- Name: trades; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.trades (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    proposer_student_id uuid NOT NULL,
    counterpart_student_id uuid NOT NULL,
    description text,
    policy_snapshot_id uuid,
    transaction_group_id uuid,
    counterpart_decided_at timestamp with time zone,
    teacher_decided_at timestamp with time zone,
    expires_at timestamp with time zone,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_trades_distinct_students CHECK ((proposer_student_id <> counterpart_student_id)),
    CONSTRAINT ck_trades_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'AWAITING_COUNTERPART'::character varying, 'AWAITING_TEACHER'::character varying, 'SETTLING'::character varying, 'COMPLETED'::character varying, 'CANCELED'::character varying, 'DECLINED'::character varying, 'EXPIRED'::character varying, 'REJECTED'::character varying, 'FAILED'::character varying])::text[])))
);


--
-- Name: transaction_business_refs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transaction_business_refs (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    transaction_group_id uuid NOT NULL,
    ref_type character varying(40) NOT NULL,
    ref_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: transaction_groups; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transaction_groups (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    transaction_type character varying(40) NOT NULL,
    actor_type character varying(40) NOT NULL,
    actor_id uuid,
    policy_snapshot_id uuid,
    recovery_of_group_id uuid,
    root_transaction_group_id uuid,
    request_id character varying(128) NOT NULL,
    correlation_id character varying(128) NOT NULL,
    reason text,
    committed_at timestamp with time zone,
    rejected_at timestamp with time zone,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_transaction_groups_recovery CHECK ((((recovery_of_group_id IS NULL) AND (root_transaction_group_id = id)) OR ((recovery_of_group_id IS NOT NULL) AND (recovery_of_group_id <> id) AND (root_transaction_group_id IS NOT NULL) AND (root_transaction_group_id <> id)))),
    CONSTRAINT ck_transaction_groups_status CHECK (((status)::text = ANY ((ARRAY['RECEIVED'::character varying, 'VALIDATING'::character varying, 'COMMITTED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: transaction_inquiries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.transaction_inquiries (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    transaction_group_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    question text NOT NULL,
    resolution text,
    resolved_by_user_id uuid,
    resolved_at timestamp with time zone,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    status_changed_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_transaction_inquiries_status CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'ANSWERED'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id uuid NOT NULL,
    external_subject character varying(128) NOT NULL,
    display_name character varying(120) NOT NULL,
    user_type character varying(40) NOT NULL,
    status character varying(40) DEFAULT 'ACTIVE'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_users_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying])::text[])))
);


--
-- Name: vote_eligibilities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vote_eligibilities (
    id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    vote_id uuid NOT NULL,
    classroom_student_id uuid NOT NULL,
    eligibility_token_hash character varying(128) NOT NULL,
    used_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: votes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.votes (
    activity_session_id uuid NOT NULL,
    classroom_id uuid NOT NULL,
    title character varying(160) NOT NULL,
    description text,
    anonymous boolean DEFAULT true NOT NULL,
    status character varying(40) NOT NULL,
    state_version bigint DEFAULT 0 NOT NULL,
    opens_at timestamp with time zone,
    closes_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ck_votes_status CHECK (((status)::text = ANY ((ARRAY['READY'::character varying, 'OPEN'::character varying, 'CLOSED'::character varying, 'RESULT_PUBLISHED'::character varying])::text[])))
);


--
-- Data for Name: activity_participants; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.activity_participants (id, classroom_id, activity_session_id, classroom_student_id, participant_status, joined_at, completed_at, created_at) FROM stdin;
\.


--
-- Data for Name: activity_presentation_slides; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.activity_presentation_slides (id, activity_session_id, sequence_number, title, image_url, created_at) FROM stdin;
\.


--
-- Data for Name: activity_sessions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.activity_sessions (id, classroom_id, activity_type, policy_snapshot_id, created_by_user_id, public_display_enabled, starts_at, ends_at, status, state_version, status_changed_at, created_at, updated_at, public_fields, presentation_image_url, presentation_title, presentation_cursor) FROM stdin;
\.


--
-- Data for Name: ai_artifacts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ai_artifacts (id, classroom_id, ai_run_id, artifact_type, content, schema_version, safety_status, created_at) FROM stdin;
\.


--
-- Data for Name: ai_decisions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ai_decisions (id, classroom_id, ai_proposal_id, decided_by_user_id, decision, reason, decided_at, created_at) FROM stdin;
\.


--
-- Data for Name: ai_proposals; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ai_proposals (id, classroom_id, ai_run_id, proposal_type, proposed_values, allowed_ranges, expires_at, applied_policy_version_id, status, state_version, status_changed_at, created_at, updated_at, source_type, severity, metric_code, direction, dedupe_key) FROM stdin;
\.


--
-- Data for Name: ai_runs; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ai_runs (id, classroom_id, async_operation_id, requested_by_user_id, run_type, model_provider, model_id, prompt_version, input_scope, validation_result, requested_at, completed_at, error_code, retention_until, status, state_version, status_changed_at, created_at, updated_at, trigger_type, analysis_period_from, analysis_period_to) FROM stdin;
\.


--
-- Data for Name: async_operations; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.async_operations (id, classroom_id, operation_type, requested_by_user_id, progress_percent, result_location, error_code, error_detail, started_at, completed_at, expires_at, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: audit_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.audit_events (id, classroom_id, transaction_group_id, actor_type, actor_id, action, target_type, target_id, request_id, correlation_id, before_summary, after_summary, occurred_at, retention_until) FROM stdin;
\.


--
-- Data for Name: autonomous_schedule_modules; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.autonomous_schedule_modules (id, classroom_id, autonomous_schedule_id, module_code, created_at) FROM stdin;
\.


--
-- Data for Name: autonomous_schedules; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.autonomous_schedules (id, classroom_id, day_of_week, local_start_time, local_end_time, time_zone, enabled, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: character_catalog; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.character_catalog (id, character_code, name, source_title, source_url, rights_status, rights_evidence_key, reviewed_at, status, created_at, updated_at) FROM stdin;
3312d206-4974-f807-410e-499d651ad172	bg_spring	봄 벚꽃	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	ACTIVE	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.08317+00
4bac56f1-b47a-c7a5-bdb2-854b0333b7c8	bg_summer_sea	여름 바다	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	ACTIVE	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.08317+00
cc156ee2-8bd5-25ae-bf34-9648b148556e	bg_autumn	가을 단풍	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	ACTIVE	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.08317+00
3f63997a-543e-2b8b-d957-76d9372a5091	bg_winter	겨울 눈	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	ACTIVE	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.08317+00
a586e776-3329-730f-d3d5-8033688fdd8c	bg_space	우주 배경	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	ACTIVE	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.08317+00
9b30244f-4b4d-0887-5446-7298bf2f3cfc	hat_red_cap	빨간 캡	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.136356+00	ACTIVE	2026-08-10 00:25:58.136356+00	2026-08-10 00:25:58.136356+00
f745353e-f66b-806b-068c-8f16119a7614	hat_teal_beanie	비니	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.136356+00	ACTIVE	2026-08-10 00:25:58.136356+00	2026-08-10 00:25:58.136356+00
4771f603-e48e-03a2-a0f6-7cc6895d95e7	face_sunglasses	선글라스	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.136356+00	ACTIVE	2026-08-10 00:25:58.136356+00	2026-08-10 00:25:58.136356+00
ff02769b-91a5-5364-79b6-98a4035b813b	face_headphones	헤드폰	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.136356+00	ACTIVE	2026-08-10 00:25:58.136356+00	2026-08-10 00:25:58.136356+00
91c601bd-760e-d128-c6b4-3cc1ba53a8f2	top_blue_tshirt	파란 티셔츠	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.136356+00	ACTIVE	2026-08-10 00:25:58.136356+00	2026-08-10 00:25:58.136356+00
4bd27711-d0b6-4cc4-be8a-f8cf6467f5f3	top_yellow_hoodie	노란 후드	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.136356+00	ACTIVE	2026-08-10 00:25:58.136356+00	2026-08-10 00:25:58.136356+00
0d1479c7-cb08-1454-36a2-5a4ae8d24657	top_green_vest	초록 조끼	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.136356+00	ACTIVE	2026-08-10 00:25:58.136356+00	2026-08-10 00:25:58.136356+00
7cf0a389-062c-fc2e-a8b1-3ba45390c10a	shoes_sneakers	운동화	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.136356+00	ACTIVE	2026-08-10 00:25:58.136356+00	2026-08-10 00:25:58.136356+00
6a578a15-e717-6226-8d4e-9d82aaa93dde	back_backpack	배낭	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.136356+00	ACTIVE	2026-08-10 00:25:58.136356+00	2026-08-10 00:25:58.136356+00
8a65a890-3233-eee5-c7ef-b5b51c568407	bottom_overalls	멜빵바지	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.136356+00	RETIRED	2026-08-10 00:25:58.136356+00	2026-08-10 00:25:58.356528+00
814aff1e-113d-7a97-17b8-0489c68227de	hat_straw	밀짚모자	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
a6d41ba5-9dde-480a-387b-6c0c540f8388	hat_beach	비치 모자	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
333b7056-3d25-9ff8-a181-3f196bf97a5e	hat_pom_beanie	방울 비니	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
04df4583-07a1-fc0b-80c4-682c2c33b8ff	hat_baseball_blue	야구 모자 · 파랑	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
542bdc7b-b485-ff5c-85d7-aeeb42c0154c	hat_baseball_red	야구 모자 · 빨강	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
f495fba5-b9fd-1998-1a7d-53eabb0d915a	hat_baseball_navy	야구 모자 · 남색	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
b8706206-f2bf-cba5-d0ef-701678382312	hat_baseball_green	야구 모자 · 초록	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
4a25eac5-c794-a77b-87d7-918ff34cd6ff	hat_baseball_yellow	야구 모자 · 노랑	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
23be9e26-d991-5e5f-3a54-0a6ebf9deba2	hat_chef	요리사 모자	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
4c2fb9be-4380-c3e6-eb68-05d3678ca04f	hat_safari	탐험가 모자	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
cb51295c-4ce1-055f-bfa6-64639e37e34f	hat_space_helmet	우주 헬멧	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
988a01fa-5900-9a66-ae7f-ae9d76188ed4	hat_safety_police	안전 경찰 모자	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
f934d977-d870-354c-881c-8e7f93dad803	hat_pilot	파일럿 모자	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
a7fb2a42-7361-2b79-5258-11287fcdb75b	hat_crown	왕관	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
f955a957-7f7e-60b0-4d71-403e2b62da11	hat_wizard	마법사 모자	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
4c853ac7-ec41-d051-67d4-e554c6ad17c6	hat_knight	기사 투구	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
d5b48b0f-c352-ec74-646e-c88666260607	hat_dinosaur	공룡 후드	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
0579d71c-d1d3-9afb-a7a0-610bcd7dbedd	face_goggles	물안경	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
c1ecf35a-1e72-0a91-3770-7b3b22a28b7c	face_ski_goggles	스키 고글	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
5480f63a-41c3-2ae7-6c62-9d5a98f79e1d	neck_scarf	목도리	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
c2525f69-9798-4d8d-9de1-33b72e6d2f9f	neck_knit_scarf	니트 목도리	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
63257d98-5389-d2a4-4b65-45f4b56158f6	top_swimsuit	수영복	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
257bec32-77c1-b6a0-0b53-b6c8c7d0d8f2	top_padding	패딩 점퍼	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
ff33e7d2-d7e7-58dd-694d-589762273b06	top_soccer	축구 유니폼	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
44920cc8-696e-e77f-7d1a-1fc4b32ff769	top_baseball_pinstripe	핀스트라이프	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
d5dd035e-3954-4418-0f97-4b0510cac980	top_baseball_red	야구복 · 빨강	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
690e0da4-997b-85ea-f70c-b760b7a811f2	top_baseball_blue	야구복 · 파랑	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
d84d8b41-f3a2-4205-9e7a-f313a1a46328	top_baseball_navy	야구복 · 남색	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
813a2c35-cf16-f203-2006-e41c09827a3a	top_baseball_gray	원정 회색	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
9e1f6121-99ec-4e94-ee76-ac68f639c47b	top_spacesuit	우주복	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
6cc290ec-2299-9726-8ef9-345d79af8efe	top_safety_police	안전 경찰복	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
7e366bdf-d4fc-2ca7-7c5f-fbb4035ec1c2	top_legal_professional	법조인 정장	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
b33e2569-9fb1-cefd-520a-fe23edc3e1dc	top_pilot	파일럿 제복	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
6b9d984b-5ac7-8c3d-b159-c8cd5dadaac0	top_wizard_robe	마법사 로브	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
315e3f49-4ee1-157c-adef-5967ea2b33d3	top_fantasy_armor	판타지 갑옷	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
0875c77a-2cec-bd87-ca48-a9ecce02c8ff	top_dinosaur	공룡 옷	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
100ae238-2a98-162e-d5d2-a8005c2455b7	shoes_red_boots	빨간 장화	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
f249ceaa-5150-178a-4029-7a272a868734	shoes_sandals	샌들	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
d6d503c7-d948-c661-29db-9b45ff1b193b	shoes_aqua	아쿠아슈즈	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
2d56578f-93a5-eac2-9129-84cd2e0b7eca	shoes_snow_boots	스노우 부츠	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
5e82c9e4-c291-abc4-5bc3-be92485a566b	shoes_space_boots	우주 부츠	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
f6864608-fe5f-3851-0064-644b1bd0280e	back_briefcase	서류 가방	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
b2e45267-76ce-dc67-1d2e-0d1aa3389771	back_fairy_wings	요정 날개	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
6a4c6402-85fa-d4a0-e6f1-84217770b5ac	back_dinosaur_tail	공룡 꼬리	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
5883ff8a-0403-ed28-c3c4-8481e34ed52a	hand_baseball_glove	야구 글러브	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
aa644179-833f-6225-a3f5-ea20b670a778	hand_magic_wand	별빛 마법봉	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
f19bed18-48f7-1d94-c4ff-2b31f1ff1192	hand_toy_sword	장난감 검	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
89f0bccf-edf1-bb0b-6737-1a0e3f26df02	effect_swim_tube	튜브	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
4828b557-b801-35b1-3b37-7e98ebabf0b9	effect_fluffy_cloud	몽실 구름	EcoVillage code-rendered pixel catalog	repo://student-app/src/features/character/pixelRenderer.ts	ORIGINAL	repo:code-pixel-catalog-v1	2026-08-10 00:25:58.08317+00	RETIRED	2026-08-10 00:25:58.08317+00	2026-08-10 00:25:58.36884+00
\.


--
-- Data for Name: character_equipment; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.character_equipment (id, classroom_id, classroom_student_id, character_profile_id, inventory_item_id, slot, state_version, equipped_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: classroom_characters; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.classroom_characters (id, classroom_id, character_catalog_id, store_product_id, display_name, sort_order, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: classroom_modules; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.classroom_modules (id, classroom_id, module_code, config_json, enabled_at, stopped_at, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: classroom_policy_versions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.classroom_policy_versions (id, classroom_id, policy_area, version, "values", schema_version, source, status, effective_from, superseded_at, created_at) FROM stdin;
\.


--
-- Data for Name: classroom_students; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.classroom_students (id, classroom_id, salary_role_id, roster_number, nickname, joined_at, retired_at, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: classroom_teachers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.classroom_teachers (id, classroom_id, user_id, role, assigned_at, ended_at, created_at) FROM stdin;
\.


--
-- Data for Name: classrooms; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.classrooms (id, school_id, course_template_version_id, classroom_code, grade, scale_label, actual_starts_on, actual_ends_on, app_mode_override, time_zone, status, state_version, status_changed_at, created_at, updated_at, currency_name, planned_student_count, display_name, class_number, description) FROM stdin;
\.


--
-- Data for Name: course_template_modules; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.course_template_modules (id, course_template_version_id, module_code, recommendation_level, sort_order, config_json, created_at, updated_at) FROM stdin;
0518fcbb-1c15-c669-50f6-08cef08ac006	c1000000-0000-4000-8000-000000000503	STORE	CORE	4	{"label": "상점", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
61889bb7-0a87-a3f0-e7ce-d1931c0dbdb2	c1000000-0000-4000-8000-000000000401	QUIZ_GAME	CORE	1	{"label": "게임형 퀴즈", "prerequisites": [], "exclusion_impact": "1회 코스의 핵심 체험이 사라집니다."}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
76e994f0-df80-4ea9-60bb-a8644ce8d9b8	c1000000-0000-4000-8000-000000000401	QUIZ_TEST	OPTIONAL	2	{"label": "평가형 퀴즈", "prerequisites": [], "exclusion_impact": "개인별 이해도 점검이 사라집니다."}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
db25ae6f-51a6-010f-b37a-fb64cdcc54b7	c1000000-0000-4000-8000-000000000402	BUDGET	FOUNDATION	1	{"label": "예산관리", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
ab6efbec-0131-f3c0-c947-29a1dbf3e49b	c1000000-0000-4000-8000-000000000402	STORE	CORE	3	{"label": "상점·소비", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
b8fbbbce-1875-718a-985b-722f89015910	c1000000-0000-4000-8000-000000000402	DEPOSIT	CORE	4	{"label": "정기예금", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
9136f488-ad2f-61bf-102c-41b901ccdebb	c1000000-0000-4000-8000-000000000402	CHARACTER	OPTIONAL	5	{"label": "캐릭터", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
f19c1fa0-87d7-dc74-aef5-b6fee52bf8e1	c1000000-0000-4000-8000-000000000402	PUBLIC_FUND	OPTIONAL	100	{"label": "공동기금 기초", "prerequisites": ["ROLE_SALARY"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
24ef0454-0eb7-9d10-16fd-3615e8c9525e	c1000000-0000-4000-8000-000000000402	QUIZ_GAME	OPTIONAL	101	{"label": "게임형 퀴즈", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
7c698ae7-e4bf-256c-0f11-e7ba54d561b3	c1000000-0000-4000-8000-000000000403	BUDGET	FOUNDATION	1	{"label": "예산관리", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
a6c89d61-a7c2-bab6-44d2-fc06ada21099	c1000000-0000-4000-8000-000000000403	STORE	CORE	3	{"label": "상점·소비", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
c9f4ca43-665f-8f9b-7dd5-c8a7866b77a8	c1000000-0000-4000-8000-000000000403	DEPOSIT	CORE	4	{"label": "정기예금", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
c34e3e0b-9cbe-b001-55e8-361031b1bf00	c1000000-0000-4000-8000-000000000403	PUBLIC_FUND	CORE	5	{"label": "공동기금·투표", "prerequisites": ["ROLE_SALARY"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
14b720af-83e9-5b9a-6c80-f28b66e75462	c1000000-0000-4000-8000-000000000403	CHARACTER	CORE	6	{"label": "캐릭터", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
de88a656-1ac5-7c11-49d6-86ed2dfefa7e	c1000000-0000-4000-8000-000000000403	TRADE	OPTIONAL	7	{"label": "개인거래", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
fe554e57-1685-5594-58ca-94c10e6b497b	c1000000-0000-4000-8000-000000000403	PHYSICAL_STORE	OPTIONAL	100	{"label": "실제 상품", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
b51aeb54-fbec-123f-d8c2-5cded096decf	c1000000-0000-4000-8000-000000000403	QUIZ_GAME	OPTIONAL	101	{"label": "퀴즈", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
c190dd5e-7337-0adb-3be3-27fa63795f9c	c1000000-0000-4000-8000-000000000403	AI_EVENT	OPTIONAL	102	{"label": "AI 경제 이벤트", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
00e9cff4-c6cd-38d2-ed01-51a7289439cb	c1000000-0000-4000-8000-000000000404	BUDGET	FOUNDATION	1	{"label": "예산관리", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
949e8dfd-9940-e251-b9e8-0a379cefddbd	c1000000-0000-4000-8000-000000000404	PUBLIC_FUND	FOUNDATION	1	{"label": "공동기금", "prerequisites": ["INCOME_TAX"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
be2e5eeb-d783-e927-8613-b2e40078359d	c1000000-0000-4000-8000-000000000404	PAYSLIP	CORE	3	{"label": "간단한 명세서", "prerequisites": ["ROLE_SALARY"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
ac243a75-92ee-a8ec-e2df-adaa7d960271	c1000000-0000-4000-8000-000000000404	STORE	CORE	4	{"label": "상점", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
ce3f1730-dd62-594c-3654-47847c8d2e7d	c1000000-0000-4000-8000-000000000404	DEPOSIT	CORE	5	{"label": "정기예금", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
de5c7dd3-f110-8367-21eb-0f7c46beeb09	c1000000-0000-4000-8000-000000000404	PUBLIC_BUDGET	CORE	6	{"label": "공동기금·공공예산·투표", "prerequisites": ["PUBLIC_FUND", "INCOME_TAX"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
90edef95-3d53-275e-2020-f1016b8dff9c	c1000000-0000-4000-8000-000000000404	CHARACTER	OPTIONAL	100	{"label": "캐릭터", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
0b3ff7ee-a611-c1aa-3450-ba563e29f170	c1000000-0000-4000-8000-000000000404	TRADE	OPTIONAL	101	{"label": "개인거래", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
e47a4697-e943-d4cc-7344-e51a58d96687	c1000000-0000-4000-8000-000000000404	PHYSICAL_STORE	OPTIONAL	102	{"label": "실제 상품", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
867d6655-5306-7c09-707d-d04faf073f6d	c1000000-0000-4000-8000-000000000404	QUIZ_GAME	OPTIONAL	103	{"label": "퀴즈", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
abc5538d-de8c-0daa-7e87-d11ad209ea5f	c1000000-0000-4000-8000-000000000404	AI_EVENT	OPTIONAL	104	{"label": "AI 경제 이벤트", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
bf18a90a-2fad-6b08-b920-1fbdd71f54e7	c1000000-0000-4000-8000-000000000501	QUIZ_GAME	CORE	1	{"label": "게임형 퀴즈", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
052ccb23-f8b4-aeb0-fd9a-d10a8a7363c5	c1000000-0000-4000-8000-000000000501	QUIZ_TEST	OPTIONAL	2	{"label": "평가형 퀴즈", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
91d23505-50e1-a58a-7793-e86875e050fc	c1000000-0000-4000-8000-000000000502	BUDGET	FOUNDATION	1	{"label": "예산관리", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
575e0ca3-127c-6cd8-0c2e-ac2e6a12a083	c1000000-0000-4000-8000-000000000502	PAYSLIP	CORE	2	{"label": "소득·급여명세서", "prerequisites": ["ROLE_SALARY"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
a7c0c692-10d0-fafe-89cb-b575abc6afcb	c1000000-0000-4000-8000-000000000502	STORE	CORE	4	{"label": "상점", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
aa168ae7-157c-0b7e-6973-e1591f1e3f17	c1000000-0000-4000-8000-000000000502	DEPOSIT	CORE	5	{"label": "정기예금", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
e1a58779-4173-1d27-5e08-dce3c8a00b65	c1000000-0000-4000-8000-000000000502	SAVINGS	CORE	6	{"label": "정기적금", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
2017b9b8-5051-ae0b-b029-14878b7b00c5	c1000000-0000-4000-8000-000000000502	PUBLIC_FUND	OPTIONAL	100	{"label": "공동기금", "prerequisites": ["INCOME_TAX"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
d33e53d1-597b-50da-5479-33e60ed33d0b	c1000000-0000-4000-8000-000000000502	CHARACTER	OPTIONAL	101	{"label": "캐릭터", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
67d0dacd-cf2f-e912-a906-f8c7d2944d1d	c1000000-0000-4000-8000-000000000502	QUIZ_GAME	OPTIONAL	102	{"label": "게임형 퀴즈", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
46e0cd75-a0ac-767a-02bf-a687958ee9c6	c1000000-0000-4000-8000-000000000503	BUDGET	FOUNDATION	1	{"label": "예산관리", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
aad49732-94bf-6114-0a73-880ac1df2fd8	c1000000-0000-4000-8000-000000000503	PAYSLIP	CORE	2	{"label": "소득·급여명세서", "prerequisites": ["ROLE_SALARY"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
7233f958-9af0-1ede-99d6-df5e0e629a63	c1000000-0000-4000-8000-000000000503	DEPOSIT	CORE	5	{"label": "정기예금", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
89656959-2190-fc38-e9e4-7e071793a350	c1000000-0000-4000-8000-000000000503	SAVINGS	CORE	6	{"label": "정기적금", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
a7ae7403-6928-49e4-e6c2-3dbcbedd181a	c1000000-0000-4000-8000-000000000503	PUBLIC_FUND	CORE	7	{"label": "공동기금·투표", "prerequisites": ["INCOME_TAX"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
14bc1044-3dc2-a249-45a5-c00bfcd77a18	c1000000-0000-4000-8000-000000000503	CHARACTER	OPTIONAL	100	{"label": "캐릭터", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
f6b39c9b-85d2-695f-79ca-e15dbce1e819	c1000000-0000-4000-8000-000000000503	TRADE	OPTIONAL	101	{"label": "개인거래", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
170edd4f-7583-aead-636e-0b5a5eb38c9f	c1000000-0000-4000-8000-000000000503	PHYSICAL_STORE	OPTIONAL	102	{"label": "실제 상품", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
2074d1cb-c41e-e56e-2bd4-9ba10ba6b4ce	c1000000-0000-4000-8000-000000000503	QUIZ_GAME	OPTIONAL	103	{"label": "퀴즈", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
86cdda88-9f96-e768-abc0-816b0c5e62a8	c1000000-0000-4000-8000-000000000503	AI_EVENT	OPTIONAL	104	{"label": "AI 경제 이벤트", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
a34721ac-fae3-221a-329a-81b5106dacac	c1000000-0000-4000-8000-000000000504	BUDGET	FOUNDATION	1	{"label": "예산관리", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
1a6205f0-f814-4b99-c715-ca4a285f8e3a	c1000000-0000-4000-8000-000000000504	CREDIT	FOUNDATION	1	{"label": "신용 기초", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
3c26ddd5-5d97-867c-448d-485d3026658f	c1000000-0000-4000-8000-000000000504	PUBLIC_FUND	FOUNDATION	1	{"label": "공동기금", "prerequisites": ["INCOME_TAX"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
a4d0d1d4-fc7b-badf-10dc-b71aba437d9e	c1000000-0000-4000-8000-000000000504	PAYSLIP	CORE	2	{"label": "소득·급여명세서", "prerequisites": ["ROLE_SALARY"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
17d56bfa-c87b-737e-f6d5-f838281d20d7	c1000000-0000-4000-8000-000000000504	STORE	CORE	4	{"label": "상점", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
773766f4-12a7-b533-0f03-44529f4e7206	c1000000-0000-4000-8000-000000000504	DEPOSIT	CORE	5	{"label": "정기예금", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
7fcb945d-c380-4155-cc49-6efaed631fa5	c1000000-0000-4000-8000-000000000504	SAVINGS	CORE	6	{"label": "정기적금", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
c6b2c292-2f3a-9446-957e-c64ee52c3f24	c1000000-0000-4000-8000-000000000504	LOAN	CORE	7	{"label": "대출·상환", "prerequisites": ["BUDGET", "CREDIT"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
0e2b852f-edd7-7691-b1f3-3250faf24026	c1000000-0000-4000-8000-000000000504	PUBLIC_BUDGET	CORE	8	{"label": "공동기금·공공예산·투표", "prerequisites": ["INCOME_TAX", "PUBLIC_FUND"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
254482cb-d8c8-de6b-6ace-1e76f3fb520d	c1000000-0000-4000-8000-000000000504	TRADE	CORE	9	{"label": "개인거래", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
2e7195fa-761c-dfc6-2b94-bc96f91c4073	c1000000-0000-4000-8000-000000000504	CHARACTER	OPTIONAL	100	{"label": "캐릭터", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
1e6dd195-3dae-a96a-d175-9a9c4a7e5d6f	c1000000-0000-4000-8000-000000000504	PHYSICAL_STORE	OPTIONAL	101	{"label": "실제 상품", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
f1303898-7c29-805b-edd5-c3ead9b6287c	c1000000-0000-4000-8000-000000000504	QUIZ_GAME	OPTIONAL	102	{"label": "퀴즈", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
bdc99c74-52f2-007c-a0d9-83efc831bbf6	c1000000-0000-4000-8000-000000000504	AI_EVENT	OPTIONAL	103	{"label": "AI 경제 이벤트", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
7e36c713-d9bb-2643-92c2-c0be047fc1eb	c1000000-0000-4000-8000-000000000601	QUIZ_GAME	CORE	1	{"label": "게임형 퀴즈", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
09e0fdf8-3a20-7214-d622-7d604f391756	c1000000-0000-4000-8000-000000000601	QUIZ_TEST	OPTIONAL	2	{"label": "평가형 퀴즈", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
4c6ed8ff-107b-3072-bff0-56b46609ab2d	c1000000-0000-4000-8000-000000000602	STORE	FOUNDATION	1	{"label": "상점", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
1bd28e18-a519-5d8f-3d57-7825b0353f32	c1000000-0000-4000-8000-000000000602	BUDGET	FOUNDATION	1	{"label": "예산관리", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
d53eaeac-3a40-8257-d532-f069d55b7900	c1000000-0000-4000-8000-000000000602	RISK_MANAGEMENT	FOUNDATION	1	{"label": "위험관리", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
54c52fb5-4bd0-ab79-509e-c432562cf331	c1000000-0000-4000-8000-000000000602	PAYSLIP	CORE	2	{"label": "소득·급여명세서", "prerequisites": ["ROLE_SALARY"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
73c36251-5937-e1e0-3054-831b462aae9c	c1000000-0000-4000-8000-000000000602	MARKET_PRICE	CORE	4	{"label": "상점·가격 변화", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
ad42346e-e91d-c9f3-4ae8-c5b21f9c2012	c1000000-0000-4000-8000-000000000602	DEPOSIT	CORE	5	{"label": "정기예금", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
7e6215b4-300e-f66e-4c46-0f0fb691c215	c1000000-0000-4000-8000-000000000602	SAVINGS	CORE	6	{"label": "정기적금", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
cebab364-99fb-f860-b98a-7931caa43c25	c1000000-0000-4000-8000-000000000602	INVESTMENT	CORE	7	{"label": "가상 투자", "prerequisites": ["SAVINGS", "RISK_MANAGEMENT"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
7cbc2068-6cff-8f05-7839-4d938b060cbf	c1000000-0000-4000-8000-000000000602	PUBLIC_FUND	CORE	8	{"label": "공동기금", "prerequisites": ["INCOME_TAX"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
40e47e6c-f593-6d6d-dab3-1d581e189f8e	c1000000-0000-4000-8000-000000000602	CHARACTER	OPTIONAL	100	{"label": "캐릭터", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
8c420521-ec59-0b39-156d-8d64736f0e9a	c1000000-0000-4000-8000-000000000602	QUIZ_GAME	OPTIONAL	101	{"label": "퀴즈", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
e9739cb9-71cc-ba18-a1b2-7a0480b1b6eb	c1000000-0000-4000-8000-000000000602	AI_EVENT	OPTIONAL	102	{"label": "AI 경제 이벤트", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
c2270627-f30a-5a55-0f23-e04060fbb36d	c1000000-0000-4000-8000-000000000603	STORE	FOUNDATION	1	{"label": "상점", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
6768971f-5a9a-918f-6dd7-9c61c0f18efb	c1000000-0000-4000-8000-000000000603	BUDGET	FOUNDATION	1	{"label": "예산관리", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
eb67b5e5-4031-3fa8-44cb-73b23555e791	c1000000-0000-4000-8000-000000000603	RISK_MANAGEMENT	FOUNDATION	1	{"label": "위험관리", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
a086eeb9-24f9-ecba-b947-3ae87fd7cc89	c1000000-0000-4000-8000-000000000603	PUBLIC_FUND	FOUNDATION	1	{"label": "공동기금", "prerequisites": ["INCOME_TAX"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
8c8b8017-bf97-ea61-8456-7730fe29d907	c1000000-0000-4000-8000-000000000603	PAYSLIP	CORE	2	{"label": "소득·급여명세서", "prerequisites": ["ROLE_SALARY"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
93a1ac76-e3b3-2d2d-3330-144142b0aa2c	c1000000-0000-4000-8000-000000000603	MARKET_PRICE	CORE	4	{"label": "상점·가격 변화", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
5e801969-37f7-6cf2-3577-459c00de17cc	c1000000-0000-4000-8000-000000000603	DEPOSIT	CORE	5	{"label": "정기예금", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
873bcb81-2f94-94b7-ee1e-ff987ae0c153	c1000000-0000-4000-8000-000000000603	SAVINGS	CORE	6	{"label": "정기적금", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
b3296261-31aa-f13c-9852-119cf85cb640	c1000000-0000-4000-8000-000000000603	INVESTMENT	CORE	7	{"label": "가상 투자", "prerequisites": ["SAVINGS", "RISK_MANAGEMENT"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
49d7ca86-4335-8230-0b8e-9e2aac9c7de8	c1000000-0000-4000-8000-000000000603	TRADE	CORE	8	{"label": "개인거래", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
67f1cab0-ff6b-87b4-f0f5-7070378c38a8	c1000000-0000-4000-8000-000000000603	PUBLIC_BUDGET	CORE	9	{"label": "공동기금·공공예산·투표", "prerequisites": ["INCOME_TAX", "PUBLIC_FUND"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
d3fbab53-8898-cbc1-e124-d9010e617370	c1000000-0000-4000-8000-000000000603	CHARACTER	OPTIONAL	100	{"label": "캐릭터", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
0244a0da-6c31-cae4-79da-70c4011aaaa3	c1000000-0000-4000-8000-000000000603	PHYSICAL_STORE	OPTIONAL	101	{"label": "실제 상품", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
f0d90498-78a1-438b-f750-6cac76fc921b	c1000000-0000-4000-8000-000000000603	QUIZ_GAME	OPTIONAL	102	{"label": "퀴즈", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
24baefea-8042-411b-1470-adc225c910a0	c1000000-0000-4000-8000-000000000603	AI_EVENT	OPTIONAL	103	{"label": "AI 경제 이벤트", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
b2818e83-5126-9d6d-b4cd-25d90a544760	c1000000-0000-4000-8000-000000000604	STORE	FOUNDATION	1	{"label": "상점", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
ea34a2a0-e640-8de2-dd06-54cbbfbb5376	c1000000-0000-4000-8000-000000000604	BUDGET	FOUNDATION	1	{"label": "예산관리", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
c628b892-d5b9-67ac-6da0-b65216a23f63	c1000000-0000-4000-8000-000000000604	RISK_MANAGEMENT	FOUNDATION	1	{"label": "위험관리", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
ab841604-2eae-e3f4-861e-efa21945964a	c1000000-0000-4000-8000-000000000604	CREDIT	FOUNDATION	1	{"label": "신용 기초", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
a25b1a12-a85f-fb76-ff82-a1dd9df17913	c1000000-0000-4000-8000-000000000604	PUBLIC_FUND	FOUNDATION	1	{"label": "공동기금", "prerequisites": ["INCOME_TAX"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
823176b2-e0a1-e496-6dfb-1d9a4339bc44	c1000000-0000-4000-8000-000000000604	PAYSLIP	CORE	2	{"label": "소득·급여명세서", "prerequisites": ["ROLE_SALARY"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
edfe3ebb-38cd-832e-69d4-8348e218ae4a	c1000000-0000-4000-8000-000000000604	MARKET_PRICE	CORE	4	{"label": "상점·가격 변화", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
d6f0beab-9516-2291-9e1f-22030c542f8e	c1000000-0000-4000-8000-000000000604	DEPOSIT	CORE	5	{"label": "정기예금", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
a8dffae5-8331-0da7-e3b3-45a5c0a9c71c	c1000000-0000-4000-8000-000000000604	SAVINGS	CORE	6	{"label": "정기적금", "prerequisites": ["BUDGET"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
99064007-6aaa-f15b-b33a-1ea4802e7a33	c1000000-0000-4000-8000-000000000604	INVESTMENT	CORE	7	{"label": "가상 투자", "prerequisites": ["SAVINGS", "RISK_MANAGEMENT"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
d0525c08-2846-ddbb-2f7c-4e592411797d	c1000000-0000-4000-8000-000000000604	LOAN	CORE	8	{"label": "대출·상환", "prerequisites": ["BUDGET", "CREDIT"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
44bac06e-275f-ce9d-87e4-9c157a2e93a9	c1000000-0000-4000-8000-000000000604	TRADE	CORE	9	{"label": "개인거래", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
3a546af4-2d2c-a8f2-1f3d-a5812dc9a75b	c1000000-0000-4000-8000-000000000604	PUBLIC_BUDGET	CORE	10	{"label": "공동기금·공공예산·투표", "prerequisites": ["INCOME_TAX", "PUBLIC_FUND"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
9586b88c-c1e9-ad2c-4a74-b688d75d3f2b	c1000000-0000-4000-8000-000000000604	CUMULATIVE_REPORT	CORE	11	{"label": "누적 리포트", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
edd7146e-01f4-327f-3842-c392c021b392	c1000000-0000-4000-8000-000000000604	CHARACTER	OPTIONAL	100	{"label": "캐릭터", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
020baa5c-a434-ec46-a8f2-ed5f9145d966	c1000000-0000-4000-8000-000000000604	PHYSICAL_STORE	OPTIONAL	101	{"label": "실제 상품", "prerequisites": ["STORE"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
ec9dc695-1abd-e10c-3744-898aa1a3a7f1	c1000000-0000-4000-8000-000000000604	QUIZ_GAME	OPTIONAL	102	{"label": "퀴즈", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
c489c3cd-b524-50ff-5177-369ea1cce641	c1000000-0000-4000-8000-000000000604	AI_EVENT	OPTIONAL	103	{"label": "AI 경제 이벤트", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
14f3680f-9d31-03da-ada2-8cbd5beaff08	c1000000-0000-4000-8000-000000000402	ROLE_SALARY	CORE	2	{"label": "소득·역할별 급여", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
e4f4773d-19c2-f425-7e73-3b9462f9fcf9	c1000000-0000-4000-8000-000000000403	ROLE_SALARY	CORE	2	{"label": "소득·급여", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
c0baf0d2-09da-441d-20d4-ab506ff7905f	c1000000-0000-4000-8000-000000000404	ROLE_SALARY	CORE	2	{"label": "소득·급여", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
a7fec1f5-1b50-d05a-fd67-c747397170aa	c1000000-0000-4000-8000-000000000404	INCOME_TAX	CORE	7	{"label": "세금 기초", "prerequisites": ["ROLE_SALARY", "PAYSLIP"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
0259cf8a-63fb-2ca8-38ec-feb909948b12	c1000000-0000-4000-8000-000000000502	ROLE_SALARY	FOUNDATION	1	{"label": "소득·급여", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
f9bf9c61-9168-8900-9fa5-475a6766a1f0	c1000000-0000-4000-8000-000000000502	INCOME_TAX	CORE	3	{"label": "세금·공제", "prerequisites": ["ROLE_SALARY", "PAYSLIP"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
fd4d6d13-2a3c-9315-701f-d47342c1e222	c1000000-0000-4000-8000-000000000503	ROLE_SALARY	FOUNDATION	1	{"label": "소득·급여", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
e7c14384-cdf9-610e-ff9b-a3345b373dd0	c1000000-0000-4000-8000-000000000503	INCOME_TAX	CORE	3	{"label": "세금·부가세", "prerequisites": ["ROLE_SALARY", "PAYSLIP"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
09e14d93-8c9e-78f5-64ed-19710436dd96	c1000000-0000-4000-8000-000000000504	ROLE_SALARY	FOUNDATION	1	{"label": "소득·급여", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
558c7529-be47-5682-fdc8-6da6319e622d	c1000000-0000-4000-8000-000000000504	INCOME_TAX	CORE	3	{"label": "세금·부가세", "prerequisites": ["ROLE_SALARY", "PAYSLIP"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
5c74d3a1-c478-9053-8819-8f377790eee9	c1000000-0000-4000-8000-000000000602	ROLE_SALARY	FOUNDATION	1	{"label": "소득·급여", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
bb1aef28-b42c-b88c-3de1-8057434c3d70	c1000000-0000-4000-8000-000000000602	INCOME_TAX	CORE	3	{"label": "세금·부가세", "prerequisites": ["ROLE_SALARY", "PAYSLIP"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
71c496e1-25ba-99d3-d3a0-eb24572d7164	c1000000-0000-4000-8000-000000000603	ROLE_SALARY	FOUNDATION	1	{"label": "소득·급여", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
a6ebe029-c62c-f373-f8b2-b4f553b8109f	c1000000-0000-4000-8000-000000000603	INCOME_TAX	CORE	3	{"label": "세금·부가세", "prerequisites": ["ROLE_SALARY", "PAYSLIP"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
a91f30f9-307e-9a86-b3da-628f0cd394cc	c1000000-0000-4000-8000-000000000604	ROLE_SALARY	FOUNDATION	1	{"label": "소득·급여", "prerequisites": []}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
71dc208d-8208-7e8f-dcd8-322266212c9c	c1000000-0000-4000-8000-000000000604	INCOME_TAX	CORE	3	{"label": "세금·부가세", "prerequisites": ["ROLE_SALARY", "PAYSLIP"]}	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:58.152783+00
\.


--
-- Data for Name: course_template_versions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.course_template_versions (id, course_template_id, version, difficulty_profile, status, published_at, retired_at, created_at, updated_at) FROM stdin;
c1000000-0000-4000-8000-000000000401	c0000000-0000-4000-8000-000000000401	1	{"profile": "GRADE_4_BASIC", "data_version": "2026.07.29", "effective_on": "2026-07-29", "learning_focus": "돈의 역할, 소비 선택, 예금 기초", "recommendation_reason": "짧은 활동에서 게임과 평가형 퀴즈로 경제 개념을 체험합니다."}	PUBLISHED	2026-07-29 00:00:00+00	\N	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c1000000-0000-4000-8000-000000000402	c0000000-0000-4000-8000-000000000402	1	{"profile": "GRADE_4_BASIC", "data_version": "2026.07.29", "effective_on": "2026-07-29", "learning_focus": "돈 받기, 소비 후 잔액, 소비와 예금 선택", "recommendation_reason": "지갑을 기반으로 소득·소비·예금의 첫 순환을 경험합니다."}	PUBLISHED	2026-07-29 00:00:00+00	\N	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c1000000-0000-4000-8000-000000000403	c0000000-0000-4000-8000-000000000403	1	{"profile": "GRADE_4_BASIC", "data_version": "2026.07.29", "effective_on": "2026-07-29", "learning_focus": "소비와 예금, 저축 목표, 공동기금", "recommendation_reason": "반복 활동으로 개인 저축과 공동 선택의 결과를 확인합니다."}	PUBLISHED	2026-07-29 00:00:00+00	\N	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c1000000-0000-4000-8000-000000000404	c0000000-0000-4000-8000-000000000404	1	{"profile": "GRADE_4_BASIC", "data_version": "2026.07.29", "effective_on": "2026-07-29", "learning_focus": "반복 소비·예금, 개인 돈과 공동 돈, 선택 결과 설명", "recommendation_reason": "생활경제의 반복 사이클과 공동예산을 한 학기 동안 확장합니다."}	PUBLISHED	2026-07-29 00:00:00+00	\N	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c1000000-0000-4000-8000-000000000501	c0000000-0000-4000-8000-000000000501	1	{"profile": "GRADE_5_STANDARD", "data_version": "2026.07.29", "effective_on": "2026-07-29", "learning_focus": "급여명세서, 세금, 예금·적금, 공동기금", "recommendation_reason": "게임과 평가형 퀴즈로 초5 핵심 금융개념을 빠르게 점검합니다."}	PUBLISHED	2026-07-29 00:00:00+00	\N	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c1000000-0000-4000-8000-000000000502	c0000000-0000-4000-8000-000000000502	1	{"profile": "GRADE_5_STANDARD", "data_version": "2026.07.29", "effective_on": "2026-07-29", "learning_focus": "총소득·실수령액, 세금, 예금·적금 차이", "recommendation_reason": "급여명세서부터 두 저축상품의 차이까지 한 순환으로 연결합니다."}	PUBLISHED	2026-07-29 00:00:00+00	\N	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c1000000-0000-4000-8000-000000000503	c0000000-0000-4000-8000-000000000503	1	{"profile": "GRADE_5_STANDARD", "data_version": "2026.07.29", "effective_on": "2026-07-29", "learning_focus": "세전·실수령액, 부가세, 예금·적금 결과, 공동기금 사용", "recommendation_reason": "반복 거래와 만기 결과를 통해 세금과 계획저축을 비교합니다."}	PUBLISHED	2026-07-29 00:00:00+00	\N	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c1000000-0000-4000-8000-000000000504	c0000000-0000-4000-8000-000000000504	1	{"profile": "GRADE_5_STANDARD", "data_version": "2026.07.29", "effective_on": "2026-07-29", "learning_focus": "급여·세금·저축 연결, 대출 원금·이자·상환, 거래 책임", "recommendation_reason": "초5 범위에서 저축과 교육용 대출을 장기 생활경제로 연결합니다."}	PUBLISHED	2026-07-29 00:00:00+00	\N	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c1000000-0000-4000-8000-000000000601	c0000000-0000-4000-8000-000000000601	1	{"profile": "GRADE_6_ADVANCED", "data_version": "2026.07.29", "effective_on": "2026-07-29", "learning_focus": "세금·부가세, 가격 변화, 공공예산, 투자 위험", "recommendation_reason": "초6 심화 개념을 게임과 평가형 퀴즈로 빠르게 점검합니다."}	PUBLISHED	2026-07-29 00:00:00+00	\N	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c1000000-0000-4000-8000-000000000602	c0000000-0000-4000-8000-000000000602	1	{"profile": "GRADE_6_ADVANCED", "data_version": "2026.07.29", "effective_on": "2026-07-29", "learning_focus": "세금·공동기금, 가격 변화, 예금·적금·투자 비교", "recommendation_reason": "시장가격과 세 금융수단의 위험·수익 차이를 함께 경험합니다."}	PUBLISHED	2026-07-29 00:00:00+00	\N	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c1000000-0000-4000-8000-000000000603	c0000000-0000-4000-8000-000000000603	1	{"profile": "GRADE_6_ADVANCED", "data_version": "2026.07.29", "effective_on": "2026-07-29", "learning_focus": "가격·구매력, 위험·수익, 개인거래세, 공공예산", "recommendation_reason": "한 달 동안 가격과 자산 변화를 누적해 시장과 공공 선택을 분석합니다."}	PUBLISHED	2026-07-29 00:00:00+00	\N	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c1000000-0000-4000-8000-000000000604	c0000000-0000-4000-8000-000000000604	1	{"profile": "GRADE_6_ADVANCED", "data_version": "2026.07.29", "effective_on": "2026-07-29", "learning_focus": "화폐량·가격, 투자 위험, 대출·신용, 공공예산, 누적 결과", "recommendation_reason": "시장·투자·대출·공공예산을 누적 자산 리포트로 통합합니다."}	PUBLISHED	2026-07-29 00:00:00+00	\N	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
\.


--
-- Data for Name: course_templates; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.course_templates (id, template_code, name, grade_min, grade_max, scale_label, status, created_at, updated_at) FROM stdin;
c0000000-0000-4000-8000-000000000401	G4_ONE_SESSION	초4 1회 체험	4	4	ONE_SESSION	PUBLISHED	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c0000000-0000-4000-8000-000000000402	G4_ONE_WEEK	초4 1주 입문	4	4	ONE_WEEK	PUBLISHED	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c0000000-0000-4000-8000-000000000403	G4_ONE_MONTH	초4 1달 저축	4	4	ONE_MONTH	PUBLISHED	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c0000000-0000-4000-8000-000000000404	G4_SEMESTER	초4 1학기 생활경제	4	4	SEMESTER	PUBLISHED	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c0000000-0000-4000-8000-000000000501	G5_ONE_SESSION	초5 1회 체험	5	5	ONE_SESSION	PUBLISHED	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c0000000-0000-4000-8000-000000000502	G5_ONE_WEEK	초5 1주 금융기초	5	5	ONE_WEEK	PUBLISHED	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c0000000-0000-4000-8000-000000000503	G5_ONE_MONTH	초5 1달 세금과 저축	5	5	ONE_MONTH	PUBLISHED	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c0000000-0000-4000-8000-000000000504	G5_SEMESTER	초5 1학기 금융생활	5	5	SEMESTER	PUBLISHED	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c0000000-0000-4000-8000-000000000601	G6_ONE_SESSION	초6 1회 체험	6	6	ONE_SESSION	PUBLISHED	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c0000000-0000-4000-8000-000000000602	G6_ONE_WEEK	초6 1주 시장과 투자	6	6	ONE_WEEK	PUBLISHED	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c0000000-0000-4000-8000-000000000603	G6_ONE_MONTH	초6 1달 시장경제	6	6	ONE_MONTH	PUBLISHED	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
c0000000-0000-4000-8000-000000000604	G6_SEMESTER	초6 1학기 종합경제	6	6	SEMESTER	PUBLISHED	2026-08-10 00:25:57.981783+00	2026-08-10 00:25:57.981783+00
\.


--
-- Data for Name: credit_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.credit_events (id, classroom_id, classroom_student_id, event_type, score_delta, score_before, score_after, level_before, level_after, reason, related_type, related_id, occurred_at, created_at) FROM stdin;
\.


--
-- Data for Name: credit_profiles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.credit_profiles (classroom_student_id, classroom_id, level, score, state_version, evaluated_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: deposit_accounts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.deposit_accounts (id, classroom_id, classroom_student_id, product_version_id, principal_amount, expected_interest_amount, opened_at, matures_at, closed_at, opening_transaction_group_id, closing_transaction_group_id, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: device_links; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.device_links (id, classroom_id, classroom_student_id, device_fingerprint_hash, device_name, linked_at, last_seen_at, revoked_at, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: economic_event_drafts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.economic_event_drafts (id, classroom_id, ai_run_id, activity_session_id, title, content, economic_effect, valid_from, valid_until, status, state_version, status_changed_at, created_at, updated_at, source_type, source_proposal_id, investment_price_step_id, revealed_at) FROM stdin;
\.


--
-- Data for Name: export_operations; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.export_operations (async_operation_id, classroom_id, requested_by_user_id, export_type, filter_snapshot, result_s3_key, retention_until, created_at) FROM stdin;
\.


--
-- Data for Name: financial_product_versions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.financial_product_versions (id, classroom_id, financial_product_id, version, product_type, term_units, interest_rate_bps, early_termination_rate_bps, installment_count, installment_amount, minimum_amount, maximum_amount, status, published_at, retired_at, created_at) FROM stdin;
\.


--
-- Data for Name: financial_products; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.financial_products (id, classroom_id, product_type, name, description, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1.1	identity classroom	SQL	V1_1__identity_classroom.sql	-1079264385	ecovillage	2026-08-10 00:25:56.877277	104	t
2	1.2	ledger	SQL	V1_2__ledger.sql	1700448143	ecovillage	2026-08-10 00:25:57.04526	65	t
3	1.3	event audit	SQL	V1_3__event_audit.sql	1492847846	ecovillage	2026-08-10 00:25:57.142144	21	t
4	1.4	store trade	SQL	V1_4__store_trade.sql	295002446	ecovillage	2026-08-10 00:25:57.177569	96	t
5	1.5	finance	SQL	V1_5__finance.sql	-1272678834	ecovillage	2026-08-10 00:25:57.293343	130	t
6	1.6	activity quiz vote	SQL	V1_6__activity_quiz_vote.sql	-1468955038	ecovillage	2026-08-10 00:25:57.44536	114	t
7	1.7	ai report	SQL	V1_7__ai_report.sql	1380307305	ecovillage	2026-08-10 00:25:57.580429	70	t
8	1.8	constraints triggers	SQL	V1_8__constraints_triggers.sql	9876882	ecovillage	2026-08-10 00:25:57.666138	109	t
9	1.9	indexes	SQL	V1_9__indexes.sql	-1500938070	ecovillage	2026-08-10 00:25:57.811018	75	t
10	1.10	classroom identity and public quiz	SQL	V1_10__classroom_identity_and_public_quiz.sql	-1907967995	ecovillage	2026-08-10 00:25:57.901037	21	t
11	1.11	mvp schema alignment	SQL	V1_11__mvp_schema_alignment.sql	-669616881	ecovillage	2026-08-10 00:25:57.937236	24	t
12	1.12	seed course recommendations	SQL	V1_12__seed_course_recommendations.sql	-1772608261	ecovillage	2026-08-10 00:25:57.970327	17	t
13	1.13	activity public fields	SQL	V1_13__activity_public_fields.sql	-1628442742	ecovillage	2026-08-10 00:25:58.002785	6	t
14	1.14	ai v1 2 execution	SQL	V1_14__ai_v1_2_execution.sql	-1599235976	ecovillage	2026-08-10 00:25:58.019126	20	t
15	1.15	quiz result workflow	SQL	V1_15__quiz_result_workflow.sql	1673969556	ecovillage	2026-08-10 00:25:58.048781	4	t
16	1.16	teacher local credentials	SQL	V1_16__teacher_local_credentials.sql	649141969	ecovillage	2026-08-10 00:25:58.060282	6	t
17	1.17	student character profiles and equipment	SQL	V1_17__student_character_profiles_and_equipment.sql	-1061654272	ecovillage	2026-08-10 00:25:58.0731	44	t
18	1.18	basic character catalog	SQL	V1_18__basic_character_catalog.sql	-1144484345	ecovillage	2026-08-10 00:25:58.132169	4	t
19	1.19	canonical module catalog	SQL	V1_19__canonical_module_catalog.sql	-1026144804	ecovillage	2026-08-10 00:25:58.145323	34	t
20	1.20	investment modes and historical reference	SQL	V1_20__investment_modes_and_historical_reference.sql	601483615	ecovillage	2026-08-10 00:25:58.193363	9	t
21	1.21	classroom display settings	SQL	V1_21__classroom_display_settings.sql	544849723	ecovillage	2026-08-10 00:25:58.210451	7	t
22	1.22	property and subscription	SQL	V1_22__property_and_subscription.sql	-764152139	ecovillage	2026-08-10 00:25:58.225324	83	t
23	1.23	pending module configuration	SQL	V1_23__pending_module_configuration.sql	1183921207	ecovillage	2026-08-10 00:25:58.323295	2	t
24	1.24	character store descriptions and order	SQL	V1_24__character_store_descriptions_and_order.sql	-154113186	ecovillage	2026-08-10 00:25:58.331846	10	t
25	1.25	retire bottom overalls	SQL	V1_25__retire_bottom_overalls.sql	16546346	ecovillage	2026-08-10 00:25:58.352503	4	t
26	1.26	sell only basic and background characters	SQL	V1_26__sell_only_basic_and_background_characters.sql	846268082	ecovillage	2026-08-10 00:25:58.365128	10	t
27	1.27	retire off sale character product versions	SQL	V1_27__retire_off_sale_character_product_versions.sql	-986698329	ecovillage	2026-08-10 00:25:58.383112	6	t
28	1.28	investment market per mode	SQL	V1_28__investment_market_per_mode.sql	719163536	ecovillage	2026-08-10 00:25:58.395951	7	t
29	1.29	property resale and subscription flow	SQL	V1_29__property_resale_and_subscription_flow.sql	-2058912010	ecovillage	2026-08-10 00:25:58.410145	12	t
30	1.30	property module catalog constraints	SQL	V1_30__property_module_catalog_constraints.sql	-1293050983	ecovillage	2026-08-10 00:25:58.428807	5	t
31	1.31	investment asset return band	SQL	V1_31__investment_asset_return_band.sql	78907896	ecovillage	2026-08-10 00:25:58.441772	3	t
32	1.32	guided image lessons	SQL	V1_32__guided_image_lessons.sql	2031257713	ecovillage	2026-08-10 00:25:58.451341	2	t
33	1.33	loan installment status constraint	SQL	V1_33__loan_installment_status_constraint.sql	818765472	ecovillage	2026-08-10 00:25:58.459727	1	t
34	1.34	guided presentation slides	SQL	V1_34__guided_presentation_slides.sql	-1673973350	ecovillage	2026-08-10 00:25:58.466581	9	t
35	1.35	property contract paid at	SQL	V1_35__property_contract_paid_at.sql	-1720283075	ecovillage	2026-08-10 00:25:58.482303	1	t
36	1.36	store product images	SQL	V1_36__store_product_images.sql	979288341	ecovillage	2026-08-10 00:25:58.489768	1	t
37	1.37	property layout generation and rent deposit	SQL	V1_37__property_layout_generation_and_rent_deposit.sql	-1079225883	ecovillage	2026-08-10 00:25:58.496408	8	t
\.


--
-- Data for Name: idempotency_records; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.idempotency_records (id, classroom_id, actor_id, route_key, idempotency_key, request_hash, response_status, response_body, transaction_group_id, expires_at, created_at) FROM stdin;
\.


--
-- Data for Name: inventory_items; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.inventory_items (id, classroom_id, classroom_student_id, character_catalog_id, source_order_item_id, locked_trade_id, acquired_at, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: investment_assets; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.investment_assets (id, classroom_id, scenario_version_id, symbol, name, display_order, status, state_version, status_changed_at, created_at, updated_at, fictional_company_profile, min_return_bps, max_return_bps) FROM stdin;
\.


--
-- Data for Name: investment_markets; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.investment_markets (id, classroom_id, active_scenario_version_id, current_step, opened_at, closed_at, status, state_version, status_changed_at, created_at, updated_at, investment_mode) FROM stdin;
\.


--
-- Data for Name: investment_orders; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.investment_orders (id, classroom_id, classroom_student_id, investment_asset_id, side, quantity, executed_price, executed_price_step, transaction_group_id, submitted_at, executed_at, reject_reason, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: investment_positions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.investment_positions (id, classroom_id, classroom_student_id, investment_asset_id, quantity, average_price, state_version, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: investment_price_steps; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.investment_price_steps (id, classroom_id, investment_asset_id, step_number, price, change_rate_bps, revealed_at, created_at) FROM stdin;
\.


--
-- Data for Name: investment_scenario_versions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.investment_scenario_versions (id, classroom_id, investment_market_id, version, name, scenario_seed, status, published_at, created_at, investment_mode, scenario_source, reference_series_code, reference_event_name, reference_source_name, reference_source_url, reference_period_start, reference_period_end) FROM stdin;
\.


--
-- Data for Name: ledger_accounts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ledger_accounts (id, classroom_id, owner_type, classroom_student_id, system_owner_key, account_type, current_balance, next_entry_sequence, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: ledger_entries; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ledger_entries (id, classroom_id, transaction_group_id, ledger_account_id, account_sequence, amount, balance_before, balance_after, created_at) FROM stdin;
\.


--
-- Data for Name: loan_applications; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.loan_applications (id, classroom_id, classroom_student_id, loan_policy_version_id, requested_amount, approved_amount, credit_level_snapshot, credit_score_snapshot, decision_reason, decided_by_user_id, decided_at, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: loan_installments; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.loan_installments (id, classroom_id, loan_id, installment_number, scheduled_principal, scheduled_interest, due_at, grace_until, paid_principal, paid_interest, paid_at, transaction_group_id, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: loan_policy_versions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.loan_policy_versions (id, classroom_id, version, maximum_principal, interest_rate_bps, term_units, installment_count, grace_units, credit_adjustments, status, effective_from, superseded_at, created_at) FROM stdin;
\.


--
-- Data for Name: loans; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.loans (id, classroom_id, loan_application_id, principal_amount, applied_interest_rate_bps, credit_level_at_approval, outstanding_principal, outstanding_interest, disbursement_transaction_group_id, started_at, matures_at, closed_at, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: outbox_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.outbox_events (event_id, classroom_id, realtime_stream_id, channel_sequence, event_type, aggregate_type, aggregate_id, state_version, payload, occurred_at, published_at, publish_attempts, last_error) FROM stdin;
\.


--
-- Data for Name: payslips; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.payslips (id, classroom_id, classroom_student_id, salary_role_id, period_key, gross_amount, deduction_amount, tax_amount, net_amount, policy_snapshot_id, transaction_group_id, issued_at, created_at) FROM stdin;
\.


--
-- Data for Name: policy_snapshot_sources; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.policy_snapshot_sources (id, classroom_id, policy_snapshot_id, source_type, source_id, source_version, source_hash, created_at) FROM stdin;
\.


--
-- Data for Name: policy_snapshots; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.policy_snapshots (id, classroom_id, snapshot_hash, schema_version, calculated_values, rounding_rule, created_at) FROM stdin;
\.


--
-- Data for Name: properties; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.properties (id, classroom_id, coordinate, display_name, row_index, column_index, property_tier, is_public, is_active, allows_rent, allows_jeonse, allows_sale, allows_subscription, sale_price, deposit_amount, monthly_rent, rent_payment_cycle_days, contract_term_days, owner_kind, owner_student_id, subscription_offer_id, subscription_winner_student_id, status, state_version, status_changed_at, created_at, updated_at, layout_generation, rent_deposit_amount) FROM stdin;
\.


--
-- Data for Name: property_contract_payments; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.property_contract_payments (id, classroom_id, property_contract_id, due_at, amount, paid_at, transaction_group_id, status, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: property_contracts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.property_contracts (id, classroom_id, property_id, lease_listing_id, subscription_offer_id, subscription_application_id, contract_type, occupant_student_id, landlord_kind, landlord_student_id, deposit_amount, monthly_rent, sale_price, payment_cycle_days, contract_term_days, started_at, ends_at, next_payment_at, approved_by_user_id, decision_reason, approved_at, terminated_at, status, state_version, status_changed_at, created_at, updated_at, paid_at) FROM stdin;
\.


--
-- Data for Name: property_layouts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.property_layouts (classroom_id, row_count, column_count, state_version, updated_at, created_at, layout_generation) FROM stdin;
\.


--
-- Data for Name: property_lease_listings; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.property_lease_listings (id, classroom_id, property_id, landlord_student_id, lease_type, deposit_amount, monthly_rent, payment_cycle_days, contract_term_days, is_public, status, state_version, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: property_sale_listings; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.property_sale_listings (id, classroom_id, property_id, seller_student_id, sale_price, status, state_version, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: quiz_answers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.quiz_answers (id, classroom_id, quiz_submission_id, quiz_session_question_id, selected_option_key, free_text, is_correct, score_awarded, response_time_ms, answered_at, created_at) FROM stdin;
\.


--
-- Data for Name: quiz_options; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.quiz_options (id, classroom_id, quiz_question_version_id, option_key, option_text, is_correct, sort_order, created_at) FROM stdin;
\.


--
-- Data for Name: quiz_question_issues; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.quiz_question_issues (id, classroom_id, quiz_session_question_id, reported_by_student_id, reason, resolution, resolved_by_user_id, resolved_at, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: quiz_question_versions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.quiz_question_versions (id, classroom_id, quiz_question_id, version, content_type, question_type, status, prompt, explanation, source, ai_run_id, published_at, retired_at, created_at) FROM stdin;
\.


--
-- Data for Name: quiz_questions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.quiz_questions (id, classroom_id, created_by_user_id, content_type, title, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: quiz_result_versions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.quiz_result_versions (id, classroom_id, quiz_session_id, version, status, reward_transaction_group_id, published_at, supersedes_result_version_id, created_at) FROM stdin;
\.


--
-- Data for Name: quiz_session_questions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.quiz_session_questions (id, classroom_id, quiz_session_id, quiz_question_version_id, sequence_number, prompt_snapshot, options_snapshot, correct_answer_snapshot, score_value, time_limit_seconds, created_at) FROM stdin;
\.


--
-- Data for Name: quiz_sessions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.quiz_sessions (activity_session_id, classroom_id, mode, content_type, reward_policy_public, leaderboard_public, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: quiz_student_results; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.quiz_student_results (id, classroom_id, quiz_result_version_id, classroom_student_id, score, correct_count, rank, reward_amount, total_response_time_ms, created_at) FROM stdin;
\.


--
-- Data for Name: quiz_submissions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.quiz_submissions (id, classroom_id, quiz_session_id, classroom_student_id, mode, answer_sheet_version, submitted_at, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: realtime_streams; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.realtime_streams (id, classroom_id, stream_type, resource_id, next_sequence, retention_from_sequence, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: regrading_runs; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.regrading_runs (id, classroom_id, quiz_session_id, source_result_version_id, new_result_version_id, reason, started_by_user_id, started_at, completed_at, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: report_snapshots; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.report_snapshots (id, classroom_id, classroom_student_id, report_type, schema_version, snapshot, as_of, retention_until, created_at) FROM stdin;
\.


--
-- Data for Name: salary_roles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.salary_roles (id, classroom_id, name, gross_amount, deductions_json, sort_order, status, state_version, status_changed_at, created_at, updated_at, role_code) FROM stdin;
\.


--
-- Data for Name: savings_accounts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.savings_accounts (id, classroom_id, classroom_student_id, product_version_id, installment_amount, installment_count, paid_installment_count, principal_balance, expected_interest_amount, opened_at, matures_at, closed_at, opening_transaction_group_id, closing_transaction_group_id, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: savings_installments; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.savings_installments (id, classroom_id, savings_account_id, installment_number, scheduled_amount, due_at, retryable_until, paid_at, transaction_group_id, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: school_ai_setting_versions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.school_ai_setting_versions (id, school_id, version, enabled, reason, effective_from, superseded_at, created_by_user_id, created_at) FROM stdin;
\.


--
-- Data for Name: school_memberships; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.school_memberships (id, school_id, user_id, role, status, assigned_at, ended_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: schools; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.schools (id, school_code, name, status, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: state_transitions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.state_transitions (id, classroom_id, aggregate_type, aggregate_id, previous_status, next_status, command, actor_type, actor_id, state_version, reason_code, reason, transaction_group_id, occurred_at, created_at) FROM stdin;
\.


--
-- Data for Name: store_order_items; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.store_order_items (id, classroom_id, store_order_id, store_product_version_id, quantity, unit_price, vat_rate_bps, subtotal_amount, tax_amount, total_amount, created_at) FROM stdin;
\.


--
-- Data for Name: store_orders; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.store_orders (id, classroom_id, classroom_student_id, subtotal_amount, tax_amount, total_amount, policy_snapshot_id, transaction_group_id, received_at, operation_canceled_at, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: store_product_versions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.store_product_versions (id, classroom_id, store_product_id, version, product_kind, unit_price, vat_rate_bps, purchasable, metadata, published_at, retired_at, created_at) FROM stdin;
\.


--
-- Data for Name: store_products; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.store_products (id, classroom_id, name, description, product_kind, created_by_user_id, status, state_version, status_changed_at, created_at, updated_at, image_url) FROM stdin;
\.


--
-- Data for Name: store_stocks; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.store_stocks (store_product_id, classroom_id, available_quantity, reserved_quantity, state_version, updated_at) FROM stdin;
\.


--
-- Data for Name: student_character_profiles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.student_character_profiles (id, classroom_id, classroom_student_id, state_version, equipment_revision, equipment_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: student_credentials; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.student_credentials (classroom_student_id, classroom_id, password_hash, credential_version, changed_at) FROM stdin;
\.


--
-- Data for Name: student_sessions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.student_sessions (id, classroom_id, classroom_student_id, device_link_id, token_hash, status, expires_at, revoked_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: subscription_account_deposits; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.subscription_account_deposits (id, classroom_id, classroom_student_id, amount, deposited_at, deposit_type, transaction_group_id, created_at) FROM stdin;
\.


--
-- Data for Name: subscription_accounts; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.subscription_accounts (classroom_student_id, classroom_id, total_deposit_amount, deposit_count, last_deposited_at, auto_deposit_amount, auto_deposit_cycle_days, next_auto_deposit_at, status, state_version, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: subscription_applications; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.subscription_applications (id, classroom_id, subscription_offer_id, classroom_student_id, deposit_count_snapshot, deposit_amount_snapshot, assigned_property_id, status, applied_at, drawn_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: subscription_auto_deposit_policies; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.subscription_auto_deposit_policies (classroom_id, enabled, amount, cycle_days, state_version, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: subscription_offer_properties; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.subscription_offer_properties (classroom_id, subscription_offer_id, property_id, created_at) FROM stdin;
\.


--
-- Data for Name: subscription_offers; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.subscription_offers (id, classroom_id, title, application_opens_at, application_closes_at, sale_price, supply_quantity, minimum_deposit_count, minimum_balance, draw_method, status, drawn_at, drawn_by_user_id, state_version, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: teacher_credentials; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.teacher_credentials (teacher_user_id, email, password_hash, credential_version, changed_at) FROM stdin;
\.


--
-- Data for Name: trade_decisions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.trade_decisions (id, classroom_id, trade_id, decision_type, actor_type, actor_id, decision, reason, decided_at, created_at) FROM stdin;
\.


--
-- Data for Name: trade_item_legs; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.trade_item_legs (id, classroom_id, trade_id, inventory_item_id, from_student_id, to_student_id, created_at) FROM stdin;
\.


--
-- Data for Name: trade_money_legs; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.trade_money_legs (id, classroom_id, trade_id, from_student_id, to_student_id, amount, created_at) FROM stdin;
\.


--
-- Data for Name: trades; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.trades (id, classroom_id, proposer_student_id, counterpart_student_id, description, policy_snapshot_id, transaction_group_id, counterpart_decided_at, teacher_decided_at, expires_at, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: transaction_business_refs; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.transaction_business_refs (id, classroom_id, transaction_group_id, ref_type, ref_id, created_at) FROM stdin;
\.


--
-- Data for Name: transaction_groups; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.transaction_groups (id, classroom_id, transaction_type, actor_type, actor_id, policy_snapshot_id, recovery_of_group_id, root_transaction_group_id, request_id, correlation_id, reason, committed_at, rejected_at, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: transaction_inquiries; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.transaction_inquiries (id, classroom_id, transaction_group_id, classroom_student_id, question, resolution, resolved_by_user_id, resolved_at, status, state_version, status_changed_at, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.users (id, external_subject, display_name, user_type, status, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: vote_ballots; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.vote_ballots (id, classroom_id, vote_id, vote_option_id, eligibility_token_hash, receipt_hash, cast_at, created_at) FROM stdin;
\.


--
-- Data for Name: vote_eligibilities; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.vote_eligibilities (id, classroom_id, vote_id, classroom_student_id, eligibility_token_hash, used_at, created_at) FROM stdin;
\.


--
-- Data for Name: vote_options; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.vote_options (id, classroom_id, vote_id, label, description, sort_order, budget_amount, created_at) FROM stdin;
\.


--
-- Data for Name: votes; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.votes (activity_session_id, classroom_id, title, description, anonymous, status, state_version, opens_at, closes_at, created_at, updated_at) FROM stdin;
\.


--
-- Name: activity_participants activity_participants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activity_participants
    ADD CONSTRAINT activity_participants_pkey PRIMARY KEY (id);


--
-- Name: activity_presentation_slides activity_presentation_slides_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activity_presentation_slides
    ADD CONSTRAINT activity_presentation_slides_pkey PRIMARY KEY (id);


--
-- Name: activity_sessions activity_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activity_sessions
    ADD CONSTRAINT activity_sessions_pkey PRIMARY KEY (id);


--
-- Name: ai_artifacts ai_artifacts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_artifacts
    ADD CONSTRAINT ai_artifacts_pkey PRIMARY KEY (id);


--
-- Name: ai_decisions ai_decisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_decisions
    ADD CONSTRAINT ai_decisions_pkey PRIMARY KEY (id);


--
-- Name: ai_proposals ai_proposals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_proposals
    ADD CONSTRAINT ai_proposals_pkey PRIMARY KEY (id);


--
-- Name: ai_runs ai_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_runs
    ADD CONSTRAINT ai_runs_pkey PRIMARY KEY (id);


--
-- Name: async_operations async_operations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.async_operations
    ADD CONSTRAINT async_operations_pkey PRIMARY KEY (id);


--
-- Name: audit_events audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_events
    ADD CONSTRAINT audit_events_pkey PRIMARY KEY (id);


--
-- Name: autonomous_schedule_modules autonomous_schedule_modules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.autonomous_schedule_modules
    ADD CONSTRAINT autonomous_schedule_modules_pkey PRIMARY KEY (id);


--
-- Name: autonomous_schedules autonomous_schedules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.autonomous_schedules
    ADD CONSTRAINT autonomous_schedules_pkey PRIMARY KEY (id);


--
-- Name: character_catalog character_catalog_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.character_catalog
    ADD CONSTRAINT character_catalog_pkey PRIMARY KEY (id);


--
-- Name: character_equipment character_equipment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.character_equipment
    ADD CONSTRAINT character_equipment_pkey PRIMARY KEY (id);


--
-- Name: classroom_characters classroom_characters_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_characters
    ADD CONSTRAINT classroom_characters_pkey PRIMARY KEY (id);


--
-- Name: classroom_modules classroom_modules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_modules
    ADD CONSTRAINT classroom_modules_pkey PRIMARY KEY (id);


--
-- Name: classroom_policy_versions classroom_policy_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_policy_versions
    ADD CONSTRAINT classroom_policy_versions_pkey PRIMARY KEY (id);


--
-- Name: classroom_students classroom_students_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_students
    ADD CONSTRAINT classroom_students_pkey PRIMARY KEY (id);


--
-- Name: classroom_teachers classroom_teachers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_teachers
    ADD CONSTRAINT classroom_teachers_pkey PRIMARY KEY (id);


--
-- Name: classrooms classrooms_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classrooms
    ADD CONSTRAINT classrooms_pkey PRIMARY KEY (id);


--
-- Name: course_template_modules course_template_modules_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course_template_modules
    ADD CONSTRAINT course_template_modules_pkey PRIMARY KEY (id);


--
-- Name: course_template_versions course_template_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course_template_versions
    ADD CONSTRAINT course_template_versions_pkey PRIMARY KEY (id);


--
-- Name: course_templates course_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course_templates
    ADD CONSTRAINT course_templates_pkey PRIMARY KEY (id);


--
-- Name: credit_events credit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credit_events
    ADD CONSTRAINT credit_events_pkey PRIMARY KEY (id);


--
-- Name: credit_profiles credit_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credit_profiles
    ADD CONSTRAINT credit_profiles_pkey PRIMARY KEY (classroom_student_id);


--
-- Name: deposit_accounts deposit_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deposit_accounts
    ADD CONSTRAINT deposit_accounts_pkey PRIMARY KEY (id);


--
-- Name: device_links device_links_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_links
    ADD CONSTRAINT device_links_pkey PRIMARY KEY (id);


--
-- Name: economic_event_drafts economic_event_drafts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.economic_event_drafts
    ADD CONSTRAINT economic_event_drafts_pkey PRIMARY KEY (id);


--
-- Name: export_operations export_operations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.export_operations
    ADD CONSTRAINT export_operations_pkey PRIMARY KEY (async_operation_id);


--
-- Name: financial_product_versions financial_product_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_product_versions
    ADD CONSTRAINT financial_product_versions_pkey PRIMARY KEY (id);


--
-- Name: financial_products financial_products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_products
    ADD CONSTRAINT financial_products_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: idempotency_records idempotency_records_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_records
    ADD CONSTRAINT idempotency_records_pkey PRIMARY KEY (id);


--
-- Name: inventory_items inventory_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_items
    ADD CONSTRAINT inventory_items_pkey PRIMARY KEY (id);


--
-- Name: investment_assets investment_assets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_assets
    ADD CONSTRAINT investment_assets_pkey PRIMARY KEY (id);


--
-- Name: investment_markets investment_markets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_markets
    ADD CONSTRAINT investment_markets_pkey PRIMARY KEY (id);


--
-- Name: investment_orders investment_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_orders
    ADD CONSTRAINT investment_orders_pkey PRIMARY KEY (id);


--
-- Name: investment_positions investment_positions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_positions
    ADD CONSTRAINT investment_positions_pkey PRIMARY KEY (id);


--
-- Name: investment_price_steps investment_price_steps_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_price_steps
    ADD CONSTRAINT investment_price_steps_pkey PRIMARY KEY (id);


--
-- Name: investment_scenario_versions investment_scenario_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_scenario_versions
    ADD CONSTRAINT investment_scenario_versions_pkey PRIMARY KEY (id);


--
-- Name: ledger_accounts ledger_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ledger_accounts
    ADD CONSTRAINT ledger_accounts_pkey PRIMARY KEY (id);


--
-- Name: ledger_entries ledger_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ledger_entries
    ADD CONSTRAINT ledger_entries_pkey PRIMARY KEY (id);


--
-- Name: loan_applications loan_applications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_applications
    ADD CONSTRAINT loan_applications_pkey PRIMARY KEY (id);


--
-- Name: loan_installments loan_installments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_installments
    ADD CONSTRAINT loan_installments_pkey PRIMARY KEY (id);


--
-- Name: loan_policy_versions loan_policy_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_policy_versions
    ADD CONSTRAINT loan_policy_versions_pkey PRIMARY KEY (id);


--
-- Name: loans loans_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loans
    ADD CONSTRAINT loans_pkey PRIMARY KEY (id);


--
-- Name: outbox_events outbox_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT outbox_events_pkey PRIMARY KEY (event_id);


--
-- Name: payslips payslips_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payslips
    ADD CONSTRAINT payslips_pkey PRIMARY KEY (id);


--
-- Name: policy_snapshot_sources policy_snapshot_sources_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.policy_snapshot_sources
    ADD CONSTRAINT policy_snapshot_sources_pkey PRIMARY KEY (id);


--
-- Name: policy_snapshots policy_snapshots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.policy_snapshots
    ADD CONSTRAINT policy_snapshots_pkey PRIMARY KEY (id);


--
-- Name: properties properties_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.properties
    ADD CONSTRAINT properties_pkey PRIMARY KEY (id);


--
-- Name: property_contract_payments property_contract_payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contract_payments
    ADD CONSTRAINT property_contract_payments_pkey PRIMARY KEY (id);


--
-- Name: property_contracts property_contracts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contracts
    ADD CONSTRAINT property_contracts_pkey PRIMARY KEY (id);


--
-- Name: property_layouts property_layouts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_layouts
    ADD CONSTRAINT property_layouts_pkey PRIMARY KEY (classroom_id);


--
-- Name: property_lease_listings property_lease_listings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_lease_listings
    ADD CONSTRAINT property_lease_listings_pkey PRIMARY KEY (id);


--
-- Name: property_sale_listings property_sale_listings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_sale_listings
    ADD CONSTRAINT property_sale_listings_pkey PRIMARY KEY (id);


--
-- Name: quiz_answers quiz_answers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_answers
    ADD CONSTRAINT quiz_answers_pkey PRIMARY KEY (id);


--
-- Name: quiz_options quiz_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_options
    ADD CONSTRAINT quiz_options_pkey PRIMARY KEY (id);


--
-- Name: quiz_question_issues quiz_question_issues_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_question_issues
    ADD CONSTRAINT quiz_question_issues_pkey PRIMARY KEY (id);


--
-- Name: quiz_question_versions quiz_question_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_question_versions
    ADD CONSTRAINT quiz_question_versions_pkey PRIMARY KEY (id);


--
-- Name: quiz_questions quiz_questions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_questions
    ADD CONSTRAINT quiz_questions_pkey PRIMARY KEY (id);


--
-- Name: quiz_result_versions quiz_result_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_result_versions
    ADD CONSTRAINT quiz_result_versions_pkey PRIMARY KEY (id);


--
-- Name: quiz_session_questions quiz_session_questions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_session_questions
    ADD CONSTRAINT quiz_session_questions_pkey PRIMARY KEY (id);


--
-- Name: quiz_sessions quiz_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_sessions
    ADD CONSTRAINT quiz_sessions_pkey PRIMARY KEY (activity_session_id);


--
-- Name: quiz_student_results quiz_student_results_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_student_results
    ADD CONSTRAINT quiz_student_results_pkey PRIMARY KEY (id);


--
-- Name: quiz_submissions quiz_submissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_submissions
    ADD CONSTRAINT quiz_submissions_pkey PRIMARY KEY (id);


--
-- Name: realtime_streams realtime_streams_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realtime_streams
    ADD CONSTRAINT realtime_streams_pkey PRIMARY KEY (id);


--
-- Name: regrading_runs regrading_runs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regrading_runs
    ADD CONSTRAINT regrading_runs_pkey PRIMARY KEY (id);


--
-- Name: report_snapshots report_snapshots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_snapshots
    ADD CONSTRAINT report_snapshots_pkey PRIMARY KEY (id);


--
-- Name: salary_roles salary_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salary_roles
    ADD CONSTRAINT salary_roles_pkey PRIMARY KEY (id);


--
-- Name: savings_accounts savings_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.savings_accounts
    ADD CONSTRAINT savings_accounts_pkey PRIMARY KEY (id);


--
-- Name: savings_installments savings_installments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.savings_installments
    ADD CONSTRAINT savings_installments_pkey PRIMARY KEY (id);


--
-- Name: school_ai_setting_versions school_ai_setting_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_ai_setting_versions
    ADD CONSTRAINT school_ai_setting_versions_pkey PRIMARY KEY (id);


--
-- Name: school_memberships school_memberships_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_memberships
    ADD CONSTRAINT school_memberships_pkey PRIMARY KEY (id);


--
-- Name: schools schools_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schools
    ADD CONSTRAINT schools_pkey PRIMARY KEY (id);


--
-- Name: state_transitions state_transitions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.state_transitions
    ADD CONSTRAINT state_transitions_pkey PRIMARY KEY (id);


--
-- Name: store_order_items store_order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_order_items
    ADD CONSTRAINT store_order_items_pkey PRIMARY KEY (id);


--
-- Name: store_orders store_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_orders
    ADD CONSTRAINT store_orders_pkey PRIMARY KEY (id);


--
-- Name: store_product_versions store_product_versions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_product_versions
    ADD CONSTRAINT store_product_versions_pkey PRIMARY KEY (id);


--
-- Name: store_products store_products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_products
    ADD CONSTRAINT store_products_pkey PRIMARY KEY (id);


--
-- Name: store_stocks store_stocks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_stocks
    ADD CONSTRAINT store_stocks_pkey PRIMARY KEY (store_product_id);


--
-- Name: student_character_profiles student_character_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_character_profiles
    ADD CONSTRAINT student_character_profiles_pkey PRIMARY KEY (id);


--
-- Name: student_credentials student_credentials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_credentials
    ADD CONSTRAINT student_credentials_pkey PRIMARY KEY (classroom_student_id);


--
-- Name: student_sessions student_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_sessions
    ADD CONSTRAINT student_sessions_pkey PRIMARY KEY (id);


--
-- Name: subscription_account_deposits subscription_account_deposits_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_account_deposits
    ADD CONSTRAINT subscription_account_deposits_pkey PRIMARY KEY (id);


--
-- Name: subscription_accounts subscription_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_accounts
    ADD CONSTRAINT subscription_accounts_pkey PRIMARY KEY (classroom_student_id);


--
-- Name: subscription_applications subscription_applications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_applications
    ADD CONSTRAINT subscription_applications_pkey PRIMARY KEY (id);


--
-- Name: subscription_auto_deposit_policies subscription_auto_deposit_policies_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_auto_deposit_policies
    ADD CONSTRAINT subscription_auto_deposit_policies_pkey PRIMARY KEY (classroom_id);


--
-- Name: subscription_offer_properties subscription_offer_properties_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_offer_properties
    ADD CONSTRAINT subscription_offer_properties_pkey PRIMARY KEY (subscription_offer_id, property_id);


--
-- Name: subscription_offers subscription_offers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_offers
    ADD CONSTRAINT subscription_offers_pkey PRIMARY KEY (id);


--
-- Name: teacher_credentials teacher_credentials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_credentials
    ADD CONSTRAINT teacher_credentials_pkey PRIMARY KEY (teacher_user_id);


--
-- Name: trade_decisions trade_decisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_decisions
    ADD CONSTRAINT trade_decisions_pkey PRIMARY KEY (id);


--
-- Name: trade_item_legs trade_item_legs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_item_legs
    ADD CONSTRAINT trade_item_legs_pkey PRIMARY KEY (id);


--
-- Name: trade_money_legs trade_money_legs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_money_legs
    ADD CONSTRAINT trade_money_legs_pkey PRIMARY KEY (id);


--
-- Name: trades trades_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trades
    ADD CONSTRAINT trades_pkey PRIMARY KEY (id);


--
-- Name: transaction_business_refs transaction_business_refs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_business_refs
    ADD CONSTRAINT transaction_business_refs_pkey PRIMARY KEY (id);


--
-- Name: transaction_groups transaction_groups_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_groups
    ADD CONSTRAINT transaction_groups_pkey PRIMARY KEY (id);


--
-- Name: transaction_inquiries transaction_inquiries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_inquiries
    ADD CONSTRAINT transaction_inquiries_pkey PRIMARY KEY (id);


--
-- Name: activity_participants uq_activity_participants_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activity_participants
    ADD CONSTRAINT uq_activity_participants_scope UNIQUE (classroom_id, id);


--
-- Name: activity_participants uq_activity_participants_student; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activity_participants
    ADD CONSTRAINT uq_activity_participants_student UNIQUE (activity_session_id, classroom_student_id);


--
-- Name: activity_presentation_slides uq_activity_presentation_slides_sequence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activity_presentation_slides
    ADD CONSTRAINT uq_activity_presentation_slides_sequence UNIQUE (activity_session_id, sequence_number);


--
-- Name: activity_sessions uq_activity_sessions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activity_sessions
    ADD CONSTRAINT uq_activity_sessions_scope UNIQUE (classroom_id, id);


--
-- Name: ai_artifacts uq_ai_artifacts_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_artifacts
    ADD CONSTRAINT uq_ai_artifacts_scope UNIQUE (classroom_id, id);


--
-- Name: ai_decisions uq_ai_decisions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_decisions
    ADD CONSTRAINT uq_ai_decisions_scope UNIQUE (classroom_id, id);


--
-- Name: ai_proposals uq_ai_proposals_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_proposals
    ADD CONSTRAINT uq_ai_proposals_scope UNIQUE (classroom_id, id);


--
-- Name: ai_runs uq_ai_runs_async_operation; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_runs
    ADD CONSTRAINT uq_ai_runs_async_operation UNIQUE (async_operation_id);


--
-- Name: ai_runs uq_ai_runs_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_runs
    ADD CONSTRAINT uq_ai_runs_scope UNIQUE (classroom_id, id);


--
-- Name: async_operations uq_async_operations_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.async_operations
    ADD CONSTRAINT uq_async_operations_scope UNIQUE (classroom_id, id);


--
-- Name: autonomous_schedule_modules uq_autonomous_schedule_modules_module; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.autonomous_schedule_modules
    ADD CONSTRAINT uq_autonomous_schedule_modules_module UNIQUE (autonomous_schedule_id, module_code);


--
-- Name: autonomous_schedule_modules uq_autonomous_schedule_modules_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.autonomous_schedule_modules
    ADD CONSTRAINT uq_autonomous_schedule_modules_scope UNIQUE (classroom_id, id);


--
-- Name: autonomous_schedules uq_autonomous_schedules_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.autonomous_schedules
    ADD CONSTRAINT uq_autonomous_schedules_scope UNIQUE (classroom_id, id);


--
-- Name: autonomous_schedules uq_autonomous_schedules_time; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.autonomous_schedules
    ADD CONSTRAINT uq_autonomous_schedules_time UNIQUE (classroom_id, day_of_week, local_start_time);


--
-- Name: character_catalog uq_character_catalog_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.character_catalog
    ADD CONSTRAINT uq_character_catalog_code UNIQUE (character_code);


--
-- Name: character_equipment uq_character_equipment_inventory_item; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.character_equipment
    ADD CONSTRAINT uq_character_equipment_inventory_item UNIQUE (inventory_item_id);


--
-- Name: character_equipment uq_character_equipment_profile_slot; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.character_equipment
    ADD CONSTRAINT uq_character_equipment_profile_slot UNIQUE (character_profile_id, slot);


--
-- Name: character_equipment uq_character_equipment_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.character_equipment
    ADD CONSTRAINT uq_character_equipment_scope UNIQUE (classroom_id, id);


--
-- Name: classroom_characters uq_classroom_characters_catalog; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_characters
    ADD CONSTRAINT uq_classroom_characters_catalog UNIQUE (classroom_id, character_catalog_id);


--
-- Name: classroom_characters uq_classroom_characters_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_characters
    ADD CONSTRAINT uq_classroom_characters_scope UNIQUE (classroom_id, id);


--
-- Name: classroom_modules uq_classroom_modules_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_modules
    ADD CONSTRAINT uq_classroom_modules_code UNIQUE (classroom_id, module_code);


--
-- Name: classroom_modules uq_classroom_modules_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_modules
    ADD CONSTRAINT uq_classroom_modules_scope UNIQUE (classroom_id, id);


--
-- Name: classroom_policy_versions uq_classroom_policy_versions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_policy_versions
    ADD CONSTRAINT uq_classroom_policy_versions_scope UNIQUE (classroom_id, id);


--
-- Name: classroom_policy_versions uq_classroom_policy_versions_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_policy_versions
    ADD CONSTRAINT uq_classroom_policy_versions_version UNIQUE (classroom_id, policy_area, version);


--
-- Name: classroom_students uq_classroom_students_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_students
    ADD CONSTRAINT uq_classroom_students_scope UNIQUE (classroom_id, id);


--
-- Name: classroom_teachers uq_classroom_teachers_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_teachers
    ADD CONSTRAINT uq_classroom_teachers_scope UNIQUE (classroom_id, id);


--
-- Name: classrooms uq_classrooms_classroom_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classrooms
    ADD CONSTRAINT uq_classrooms_classroom_code UNIQUE (classroom_code);


--
-- Name: course_template_modules uq_course_template_modules_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course_template_modules
    ADD CONSTRAINT uq_course_template_modules_code UNIQUE (course_template_version_id, module_code);


--
-- Name: course_template_versions uq_course_template_versions_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course_template_versions
    ADD CONSTRAINT uq_course_template_versions_version UNIQUE (course_template_id, version);


--
-- Name: course_templates uq_course_templates_template_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course_templates
    ADD CONSTRAINT uq_course_templates_template_code UNIQUE (template_code);


--
-- Name: credit_events uq_credit_events_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credit_events
    ADD CONSTRAINT uq_credit_events_scope UNIQUE (classroom_id, id);


--
-- Name: credit_profiles uq_credit_profiles_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credit_profiles
    ADD CONSTRAINT uq_credit_profiles_scope UNIQUE (classroom_id, classroom_student_id);


--
-- Name: deposit_accounts uq_deposit_accounts_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deposit_accounts
    ADD CONSTRAINT uq_deposit_accounts_scope UNIQUE (classroom_id, id);


--
-- Name: device_links uq_device_links_fingerprint; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_links
    ADD CONSTRAINT uq_device_links_fingerprint UNIQUE (classroom_student_id, device_fingerprint_hash);


--
-- Name: device_links uq_device_links_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_links
    ADD CONSTRAINT uq_device_links_scope UNIQUE (classroom_id, id);


--
-- Name: economic_event_drafts uq_economic_event_drafts_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.economic_event_drafts
    ADD CONSTRAINT uq_economic_event_drafts_scope UNIQUE (classroom_id, id);


--
-- Name: export_operations uq_export_operations_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.export_operations
    ADD CONSTRAINT uq_export_operations_scope UNIQUE (classroom_id, async_operation_id);


--
-- Name: financial_product_versions uq_financial_product_versions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_product_versions
    ADD CONSTRAINT uq_financial_product_versions_scope UNIQUE (classroom_id, id);


--
-- Name: financial_product_versions uq_financial_product_versions_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_product_versions
    ADD CONSTRAINT uq_financial_product_versions_version UNIQUE (financial_product_id, version);


--
-- Name: financial_products uq_financial_products_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_products
    ADD CONSTRAINT uq_financial_products_scope UNIQUE (classroom_id, id);


--
-- Name: idempotency_records uq_idempotency_records_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_records
    ADD CONSTRAINT uq_idempotency_records_key UNIQUE (classroom_id, actor_id, route_key, idempotency_key);


--
-- Name: idempotency_records uq_idempotency_records_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_records
    ADD CONSTRAINT uq_idempotency_records_scope UNIQUE (classroom_id, id);


--
-- Name: inventory_items uq_inventory_items_owner; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_items
    ADD CONSTRAINT uq_inventory_items_owner UNIQUE (classroom_id, classroom_student_id, id);


--
-- Name: inventory_items uq_inventory_items_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_items
    ADD CONSTRAINT uq_inventory_items_scope UNIQUE (classroom_id, id);


--
-- Name: investment_assets uq_investment_assets_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_assets
    ADD CONSTRAINT uq_investment_assets_scope UNIQUE (classroom_id, id);


--
-- Name: investment_assets uq_investment_assets_symbol; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_assets
    ADD CONSTRAINT uq_investment_assets_symbol UNIQUE (scenario_version_id, symbol);


--
-- Name: investment_markets uq_investment_markets_mode_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_markets
    ADD CONSTRAINT uq_investment_markets_mode_scope UNIQUE (classroom_id, investment_mode, id);


--
-- Name: investment_markets uq_investment_markets_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_markets
    ADD CONSTRAINT uq_investment_markets_scope UNIQUE (classroom_id, id);


--
-- Name: investment_orders uq_investment_orders_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_orders
    ADD CONSTRAINT uq_investment_orders_scope UNIQUE (classroom_id, id);


--
-- Name: investment_positions uq_investment_positions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_positions
    ADD CONSTRAINT uq_investment_positions_scope UNIQUE (classroom_id, id);


--
-- Name: investment_positions uq_investment_positions_student_asset; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_positions
    ADD CONSTRAINT uq_investment_positions_student_asset UNIQUE (classroom_student_id, investment_asset_id);


--
-- Name: investment_price_steps uq_investment_price_steps_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_price_steps
    ADD CONSTRAINT uq_investment_price_steps_scope UNIQUE (classroom_id, id);


--
-- Name: investment_price_steps uq_investment_price_steps_step; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_price_steps
    ADD CONSTRAINT uq_investment_price_steps_step UNIQUE (investment_asset_id, step_number);


--
-- Name: investment_scenario_versions uq_investment_scenario_versions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_scenario_versions
    ADD CONSTRAINT uq_investment_scenario_versions_scope UNIQUE (classroom_id, id);


--
-- Name: investment_scenario_versions uq_investment_scenario_versions_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_scenario_versions
    ADD CONSTRAINT uq_investment_scenario_versions_version UNIQUE (investment_market_id, version);


--
-- Name: ledger_accounts uq_ledger_accounts_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ledger_accounts
    ADD CONSTRAINT uq_ledger_accounts_scope UNIQUE (classroom_id, id);


--
-- Name: ledger_entries uq_ledger_entries_account_sequence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ledger_entries
    ADD CONSTRAINT uq_ledger_entries_account_sequence UNIQUE (ledger_account_id, account_sequence);


--
-- Name: ledger_entries uq_ledger_entries_group_account; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ledger_entries
    ADD CONSTRAINT uq_ledger_entries_group_account UNIQUE (transaction_group_id, ledger_account_id);


--
-- Name: ledger_entries uq_ledger_entries_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ledger_entries
    ADD CONSTRAINT uq_ledger_entries_scope UNIQUE (classroom_id, id);


--
-- Name: loan_applications uq_loan_applications_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_applications
    ADD CONSTRAINT uq_loan_applications_scope UNIQUE (classroom_id, id);


--
-- Name: loan_installments uq_loan_installments_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_installments
    ADD CONSTRAINT uq_loan_installments_number UNIQUE (loan_id, installment_number);


--
-- Name: loan_installments uq_loan_installments_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_installments
    ADD CONSTRAINT uq_loan_installments_scope UNIQUE (classroom_id, id);


--
-- Name: loan_policy_versions uq_loan_policy_versions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_policy_versions
    ADD CONSTRAINT uq_loan_policy_versions_scope UNIQUE (classroom_id, id);


--
-- Name: loan_policy_versions uq_loan_policy_versions_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_policy_versions
    ADD CONSTRAINT uq_loan_policy_versions_version UNIQUE (classroom_id, version);


--
-- Name: loans uq_loans_application; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loans
    ADD CONSTRAINT uq_loans_application UNIQUE (loan_application_id);


--
-- Name: loans uq_loans_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loans
    ADD CONSTRAINT uq_loans_scope UNIQUE (classroom_id, id);


--
-- Name: outbox_events uq_outbox_events_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT uq_outbox_events_scope UNIQUE (classroom_id, event_id);


--
-- Name: outbox_events uq_outbox_events_sequence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT uq_outbox_events_sequence UNIQUE (realtime_stream_id, channel_sequence);


--
-- Name: payslips uq_payslips_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payslips
    ADD CONSTRAINT uq_payslips_scope UNIQUE (classroom_id, id);


--
-- Name: payslips uq_payslips_student_period; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payslips
    ADD CONSTRAINT uq_payslips_student_period UNIQUE (classroom_student_id, period_key);


--
-- Name: policy_snapshot_sources uq_policy_snapshot_sources_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.policy_snapshot_sources
    ADD CONSTRAINT uq_policy_snapshot_sources_scope UNIQUE (classroom_id, id);


--
-- Name: policy_snapshot_sources uq_policy_snapshot_sources_source; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.policy_snapshot_sources
    ADD CONSTRAINT uq_policy_snapshot_sources_source UNIQUE (policy_snapshot_id, source_type, source_id);


--
-- Name: policy_snapshots uq_policy_snapshots_hash; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.policy_snapshots
    ADD CONSTRAINT uq_policy_snapshots_hash UNIQUE (classroom_id, snapshot_hash);


--
-- Name: policy_snapshots uq_policy_snapshots_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.policy_snapshots
    ADD CONSTRAINT uq_policy_snapshots_scope UNIQUE (classroom_id, id);


--
-- Name: properties uq_properties_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.properties
    ADD CONSTRAINT uq_properties_scope UNIQUE (classroom_id, id);


--
-- Name: property_contract_payments uq_property_contract_payments_due; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contract_payments
    ADD CONSTRAINT uq_property_contract_payments_due UNIQUE (property_contract_id, due_at);


--
-- Name: property_contract_payments uq_property_contract_payments_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contract_payments
    ADD CONSTRAINT uq_property_contract_payments_scope UNIQUE (classroom_id, id);


--
-- Name: property_contracts uq_property_contracts_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contracts
    ADD CONSTRAINT uq_property_contracts_scope UNIQUE (classroom_id, id);


--
-- Name: property_lease_listings uq_property_lease_listings_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_lease_listings
    ADD CONSTRAINT uq_property_lease_listings_scope UNIQUE (classroom_id, id);


--
-- Name: property_sale_listings uq_property_sale_listings_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_sale_listings
    ADD CONSTRAINT uq_property_sale_listings_scope UNIQUE (classroom_id, id);


--
-- Name: quiz_answers uq_quiz_answers_question; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_answers
    ADD CONSTRAINT uq_quiz_answers_question UNIQUE (quiz_submission_id, quiz_session_question_id);


--
-- Name: quiz_answers uq_quiz_answers_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_answers
    ADD CONSTRAINT uq_quiz_answers_scope UNIQUE (classroom_id, id);


--
-- Name: quiz_options uq_quiz_options_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_options
    ADD CONSTRAINT uq_quiz_options_key UNIQUE (quiz_question_version_id, option_key);


--
-- Name: quiz_options uq_quiz_options_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_options
    ADD CONSTRAINT uq_quiz_options_scope UNIQUE (classroom_id, id);


--
-- Name: quiz_question_issues uq_quiz_question_issues_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_question_issues
    ADD CONSTRAINT uq_quiz_question_issues_scope UNIQUE (classroom_id, id);


--
-- Name: quiz_question_versions uq_quiz_question_versions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_question_versions
    ADD CONSTRAINT uq_quiz_question_versions_scope UNIQUE (classroom_id, id);


--
-- Name: quiz_question_versions uq_quiz_question_versions_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_question_versions
    ADD CONSTRAINT uq_quiz_question_versions_version UNIQUE (quiz_question_id, version);


--
-- Name: quiz_questions uq_quiz_questions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_questions
    ADD CONSTRAINT uq_quiz_questions_scope UNIQUE (classroom_id, id);


--
-- Name: quiz_result_versions uq_quiz_result_versions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_result_versions
    ADD CONSTRAINT uq_quiz_result_versions_scope UNIQUE (classroom_id, id);


--
-- Name: quiz_result_versions uq_quiz_result_versions_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_result_versions
    ADD CONSTRAINT uq_quiz_result_versions_version UNIQUE (quiz_session_id, version);


--
-- Name: quiz_session_questions uq_quiz_session_questions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_session_questions
    ADD CONSTRAINT uq_quiz_session_questions_scope UNIQUE (classroom_id, id);


--
-- Name: quiz_session_questions uq_quiz_session_questions_sequence; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_session_questions
    ADD CONSTRAINT uq_quiz_session_questions_sequence UNIQUE (quiz_session_id, sequence_number);


--
-- Name: quiz_sessions uq_quiz_sessions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_sessions
    ADD CONSTRAINT uq_quiz_sessions_scope UNIQUE (classroom_id, activity_session_id);


--
-- Name: quiz_student_results uq_quiz_student_results_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_student_results
    ADD CONSTRAINT uq_quiz_student_results_scope UNIQUE (classroom_id, id);


--
-- Name: quiz_student_results uq_quiz_student_results_student; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_student_results
    ADD CONSTRAINT uq_quiz_student_results_student UNIQUE (quiz_result_version_id, classroom_student_id);


--
-- Name: quiz_submissions uq_quiz_submissions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_submissions
    ADD CONSTRAINT uq_quiz_submissions_scope UNIQUE (classroom_id, id);


--
-- Name: quiz_submissions uq_quiz_submissions_student_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_submissions
    ADD CONSTRAINT uq_quiz_submissions_student_version UNIQUE (quiz_session_id, classroom_student_id, answer_sheet_version);


--
-- Name: realtime_streams uq_realtime_streams_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realtime_streams
    ADD CONSTRAINT uq_realtime_streams_scope UNIQUE (classroom_id, id);


--
-- Name: regrading_runs uq_regrading_runs_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regrading_runs
    ADD CONSTRAINT uq_regrading_runs_scope UNIQUE (classroom_id, id);


--
-- Name: report_snapshots uq_report_snapshots_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_snapshots
    ADD CONSTRAINT uq_report_snapshots_scope UNIQUE (classroom_id, id);


--
-- Name: salary_roles uq_salary_roles_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salary_roles
    ADD CONSTRAINT uq_salary_roles_scope UNIQUE (classroom_id, id);


--
-- Name: savings_accounts uq_savings_accounts_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.savings_accounts
    ADD CONSTRAINT uq_savings_accounts_scope UNIQUE (classroom_id, id);


--
-- Name: savings_installments uq_savings_installments_number; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.savings_installments
    ADD CONSTRAINT uq_savings_installments_number UNIQUE (savings_account_id, installment_number);


--
-- Name: savings_installments uq_savings_installments_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.savings_installments
    ADD CONSTRAINT uq_savings_installments_scope UNIQUE (classroom_id, id);


--
-- Name: school_ai_setting_versions uq_school_ai_setting_versions_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_ai_setting_versions
    ADD CONSTRAINT uq_school_ai_setting_versions_version UNIQUE (school_id, version);


--
-- Name: schools uq_schools_school_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schools
    ADD CONSTRAINT uq_schools_school_code UNIQUE (school_code);


--
-- Name: state_transitions uq_state_transitions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.state_transitions
    ADD CONSTRAINT uq_state_transitions_scope UNIQUE (classroom_id, id);


--
-- Name: state_transitions uq_state_transitions_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.state_transitions
    ADD CONSTRAINT uq_state_transitions_version UNIQUE (classroom_id, aggregate_type, aggregate_id, state_version);


--
-- Name: store_order_items uq_store_order_items_product; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_order_items
    ADD CONSTRAINT uq_store_order_items_product UNIQUE (store_order_id, store_product_version_id);


--
-- Name: store_order_items uq_store_order_items_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_order_items
    ADD CONSTRAINT uq_store_order_items_scope UNIQUE (classroom_id, id);


--
-- Name: store_orders uq_store_orders_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_orders
    ADD CONSTRAINT uq_store_orders_scope UNIQUE (classroom_id, id);


--
-- Name: store_product_versions uq_store_product_versions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_product_versions
    ADD CONSTRAINT uq_store_product_versions_scope UNIQUE (classroom_id, id);


--
-- Name: store_product_versions uq_store_product_versions_version; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_product_versions
    ADD CONSTRAINT uq_store_product_versions_version UNIQUE (store_product_id, version);


--
-- Name: store_products uq_store_products_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_products
    ADD CONSTRAINT uq_store_products_scope UNIQUE (classroom_id, id);


--
-- Name: store_stocks uq_store_stocks_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_stocks
    ADD CONSTRAINT uq_store_stocks_scope UNIQUE (classroom_id, store_product_id);


--
-- Name: student_character_profiles uq_student_character_profiles_owner; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_character_profiles
    ADD CONSTRAINT uq_student_character_profiles_owner UNIQUE (classroom_id, classroom_student_id, id);


--
-- Name: student_character_profiles uq_student_character_profiles_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_character_profiles
    ADD CONSTRAINT uq_student_character_profiles_scope UNIQUE (classroom_id, id);


--
-- Name: student_character_profiles uq_student_character_profiles_student; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_character_profiles
    ADD CONSTRAINT uq_student_character_profiles_student UNIQUE (classroom_id, classroom_student_id);


--
-- Name: student_credentials uq_student_credentials_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_credentials
    ADD CONSTRAINT uq_student_credentials_scope UNIQUE (classroom_id, classroom_student_id);


--
-- Name: student_sessions uq_student_sessions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_sessions
    ADD CONSTRAINT uq_student_sessions_scope UNIQUE (classroom_id, id);


--
-- Name: student_sessions uq_student_sessions_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_sessions
    ADD CONSTRAINT uq_student_sessions_token UNIQUE (token_hash);


--
-- Name: subscription_account_deposits uq_subscription_account_deposits_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_account_deposits
    ADD CONSTRAINT uq_subscription_account_deposits_scope UNIQUE (classroom_id, id);


--
-- Name: subscription_applications uq_subscription_applications_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_applications
    ADD CONSTRAINT uq_subscription_applications_scope UNIQUE (classroom_id, id);


--
-- Name: subscription_applications uq_subscription_applications_student_offer; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_applications
    ADD CONSTRAINT uq_subscription_applications_student_offer UNIQUE (subscription_offer_id, classroom_student_id);


--
-- Name: subscription_offers uq_subscription_offers_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_offers
    ADD CONSTRAINT uq_subscription_offers_scope UNIQUE (classroom_id, id);


--
-- Name: teacher_credentials uq_teacher_credentials_email; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_credentials
    ADD CONSTRAINT uq_teacher_credentials_email UNIQUE (email);


--
-- Name: trade_decisions uq_trade_decisions_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_decisions
    ADD CONSTRAINT uq_trade_decisions_scope UNIQUE (classroom_id, id);


--
-- Name: trade_decisions uq_trade_decisions_type; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_decisions
    ADD CONSTRAINT uq_trade_decisions_type UNIQUE (trade_id, decision_type);


--
-- Name: trade_item_legs uq_trade_item_legs_item; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_item_legs
    ADD CONSTRAINT uq_trade_item_legs_item UNIQUE (trade_id, inventory_item_id);


--
-- Name: trade_item_legs uq_trade_item_legs_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_item_legs
    ADD CONSTRAINT uq_trade_item_legs_scope UNIQUE (classroom_id, id);


--
-- Name: trade_money_legs uq_trade_money_legs_direction; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_money_legs
    ADD CONSTRAINT uq_trade_money_legs_direction UNIQUE (trade_id, from_student_id, to_student_id);


--
-- Name: trade_money_legs uq_trade_money_legs_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_money_legs
    ADD CONSTRAINT uq_trade_money_legs_scope UNIQUE (classroom_id, id);


--
-- Name: trades uq_trades_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trades
    ADD CONSTRAINT uq_trades_scope UNIQUE (classroom_id, id);


--
-- Name: transaction_business_refs uq_transaction_business_refs_ref; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_business_refs
    ADD CONSTRAINT uq_transaction_business_refs_ref UNIQUE (transaction_group_id, ref_type, ref_id);


--
-- Name: transaction_business_refs uq_transaction_business_refs_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_business_refs
    ADD CONSTRAINT uq_transaction_business_refs_scope UNIQUE (classroom_id, id);


--
-- Name: transaction_groups uq_transaction_groups_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_groups
    ADD CONSTRAINT uq_transaction_groups_scope UNIQUE (classroom_id, id);


--
-- Name: transaction_inquiries uq_transaction_inquiries_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_inquiries
    ADD CONSTRAINT uq_transaction_inquiries_scope UNIQUE (classroom_id, id);


--
-- Name: users uq_users_external_subject; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uq_users_external_subject UNIQUE (external_subject);


--
-- Name: vote_ballots uq_vote_ballots_receipt; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_ballots
    ADD CONSTRAINT uq_vote_ballots_receipt UNIQUE (receipt_hash);


--
-- Name: vote_ballots uq_vote_ballots_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_ballots
    ADD CONSTRAINT uq_vote_ballots_scope UNIQUE (classroom_id, id);


--
-- Name: vote_ballots uq_vote_ballots_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_ballots
    ADD CONSTRAINT uq_vote_ballots_token UNIQUE (vote_id, eligibility_token_hash);


--
-- Name: vote_eligibilities uq_vote_eligibilities_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_eligibilities
    ADD CONSTRAINT uq_vote_eligibilities_scope UNIQUE (classroom_id, id);


--
-- Name: vote_eligibilities uq_vote_eligibilities_student; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_eligibilities
    ADD CONSTRAINT uq_vote_eligibilities_student UNIQUE (vote_id, classroom_student_id);


--
-- Name: vote_eligibilities uq_vote_eligibilities_token; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_eligibilities
    ADD CONSTRAINT uq_vote_eligibilities_token UNIQUE (vote_id, eligibility_token_hash);


--
-- Name: vote_eligibilities uq_vote_eligibilities_vote_token_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_eligibilities
    ADD CONSTRAINT uq_vote_eligibilities_vote_token_scope UNIQUE (classroom_id, vote_id, eligibility_token_hash);


--
-- Name: vote_options uq_vote_options_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_options
    ADD CONSTRAINT uq_vote_options_scope UNIQUE (classroom_id, id);


--
-- Name: vote_options uq_vote_options_sort_order; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_options
    ADD CONSTRAINT uq_vote_options_sort_order UNIQUE (vote_id, sort_order);


--
-- Name: vote_options uq_vote_options_vote_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_options
    ADD CONSTRAINT uq_vote_options_vote_scope UNIQUE (classroom_id, vote_id, id);


--
-- Name: votes uq_votes_scope; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.votes
    ADD CONSTRAINT uq_votes_scope UNIQUE (classroom_id, activity_session_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: vote_ballots vote_ballots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_ballots
    ADD CONSTRAINT vote_ballots_pkey PRIMARY KEY (id);


--
-- Name: vote_eligibilities vote_eligibilities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_eligibilities
    ADD CONSTRAINT vote_eligibilities_pkey PRIMARY KEY (id);


--
-- Name: vote_options vote_options_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_options
    ADD CONSTRAINT vote_options_pkey PRIMARY KEY (id);


--
-- Name: votes votes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.votes
    ADD CONSTRAINT votes_pkey PRIMARY KEY (activity_session_id);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: ix_activity_participants_session; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_activity_participants_session ON public.activity_participants USING btree (activity_session_id);


--
-- Name: ix_activity_presentation_slides_activity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_activity_presentation_slides_activity ON public.activity_presentation_slides USING btree (activity_session_id, sequence_number);


--
-- Name: ix_activity_sessions_classroom_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_activity_sessions_classroom_status ON public.activity_sessions USING btree (classroom_id, status, starts_at DESC);


--
-- Name: ix_ai_artifacts_run; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_ai_artifacts_run ON public.ai_artifacts USING btree (ai_run_id);


--
-- Name: ix_ai_runs_classroom_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_ai_runs_classroom_status ON public.ai_runs USING btree (classroom_id, status, requested_at DESC);


--
-- Name: ix_async_operations_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_async_operations_status ON public.async_operations USING btree (classroom_id, status, created_at);


--
-- Name: ix_audit_events_classroom_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_audit_events_classroom_time ON public.audit_events USING btree (classroom_id, occurred_at DESC, id);


--
-- Name: ix_autonomous_schedule_modules_module; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_autonomous_schedule_modules_module ON public.autonomous_schedule_modules USING btree (classroom_id, module_code, autonomous_schedule_id);


--
-- Name: ix_autonomous_schedules_lookup; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_autonomous_schedules_lookup ON public.autonomous_schedules USING btree (classroom_id, day_of_week, local_start_time) WHERE (enabled = true);


--
-- Name: ix_character_equipment_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_character_equipment_owner ON public.character_equipment USING btree (classroom_id, classroom_student_id);


--
-- Name: ix_character_equipment_profile; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_character_equipment_profile ON public.character_equipment USING btree (classroom_id, character_profile_id);


--
-- Name: ix_classroom_modules_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_classroom_modules_status ON public.classroom_modules USING btree (classroom_id, status, module_code);


--
-- Name: ix_classroom_students_classroom_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_classroom_students_classroom_status ON public.classroom_students USING btree (classroom_id, status, roster_number);


--
-- Name: ix_classroom_students_salary_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_classroom_students_salary_role ON public.classroom_students USING btree (salary_role_id);


--
-- Name: ix_classroom_teachers_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_classroom_teachers_user ON public.classroom_teachers USING btree (user_id);


--
-- Name: ix_classrooms_school_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_classrooms_school_status ON public.classrooms USING btree (school_id, status);


--
-- Name: ix_credit_events_student_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_credit_events_student_time ON public.credit_events USING btree (classroom_student_id, occurred_at DESC);


--
-- Name: ix_deposit_accounts_maturity; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_deposit_accounts_maturity ON public.deposit_accounts USING btree (classroom_id, status, matures_at);


--
-- Name: ix_idempotency_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_idempotency_expires ON public.idempotency_records USING btree (expires_at);


--
-- Name: ix_inventory_items_locked_trade; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inventory_items_locked_trade ON public.inventory_items USING btree (locked_trade_id);


--
-- Name: ix_inventory_items_owner_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_inventory_items_owner_status ON public.inventory_items USING btree (classroom_student_id, status, acquired_at DESC);


--
-- Name: ix_investment_assets_scenario; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_investment_assets_scenario ON public.investment_assets USING btree (scenario_version_id);


--
-- Name: ix_investment_orders_student_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_investment_orders_student_time ON public.investment_orders USING btree (classroom_student_id, submitted_at DESC);


--
-- Name: ix_investment_price_steps_reveal; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_investment_price_steps_reveal ON public.investment_price_steps USING btree (investment_asset_id, step_number DESC);


--
-- Name: ix_ledger_entries_account_sequence; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_ledger_entries_account_sequence ON public.ledger_entries USING btree (ledger_account_id, account_sequence DESC);


--
-- Name: ix_ledger_entries_group; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_ledger_entries_group ON public.ledger_entries USING btree (transaction_group_id);


--
-- Name: ix_loan_applications_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_loan_applications_queue ON public.loan_applications USING btree (classroom_id, status, created_at);


--
-- Name: ix_loan_installments_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_loan_installments_due ON public.loan_installments USING btree (classroom_id, status, due_at);


--
-- Name: ix_loan_installments_loan; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_loan_installments_loan ON public.loan_installments USING btree (loan_id);


--
-- Name: ix_outbox_unpublished; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_outbox_unpublished ON public.outbox_events USING btree (occurred_at, event_id) WHERE (published_at IS NULL);


--
-- Name: ix_payslips_student_issued; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_payslips_student_issued ON public.payslips USING btree (classroom_student_id, issued_at DESC);


--
-- Name: ix_policy_snapshot_sources_snapshot; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_policy_snapshot_sources_snapshot ON public.policy_snapshot_sources USING btree (policy_snapshot_id);


--
-- Name: ix_properties_available; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_properties_available ON public.properties USING btree (classroom_id, status) WHERE (is_active AND is_public);


--
-- Name: ix_properties_current_layout; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_properties_current_layout ON public.properties USING btree (classroom_id, layout_generation, row_index, column_index);


--
-- Name: ix_property_contract_payments_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_property_contract_payments_due ON public.property_contract_payments USING btree (classroom_id, due_at) WHERE ((status)::text = ANY ((ARRAY['SCHEDULED'::character varying, 'MISSED'::character varying])::text[]));


--
-- Name: ix_property_contracts_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_property_contracts_due ON public.property_contracts USING btree (classroom_id, next_payment_at) WHERE ((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'OVERDUE'::character varying])::text[]));


--
-- Name: ix_property_sale_listings_classroom; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_property_sale_listings_classroom ON public.property_sale_listings USING btree (classroom_id, created_at DESC);


--
-- Name: ix_quiz_answers_submission; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_quiz_answers_submission ON public.quiz_answers USING btree (quiz_submission_id);


--
-- Name: ix_quiz_results_session_version; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_quiz_results_session_version ON public.quiz_result_versions USING btree (quiz_session_id, version DESC);


--
-- Name: ix_quiz_submissions_session_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_quiz_submissions_session_student ON public.quiz_submissions USING btree (quiz_session_id, classroom_student_id);


--
-- Name: ix_report_snapshots_retention; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_report_snapshots_retention ON public.report_snapshots USING btree (retention_until);


--
-- Name: ix_savings_installments_account; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_savings_installments_account ON public.savings_installments USING btree (savings_account_id);


--
-- Name: ix_savings_installments_due; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_savings_installments_due ON public.savings_installments USING btree (classroom_id, status, due_at);


--
-- Name: ix_state_transitions_aggregate; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_state_transitions_aggregate ON public.state_transitions USING btree (classroom_id, aggregate_type, aggregate_id, state_version DESC);


--
-- Name: ix_store_order_items_order; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_store_order_items_order ON public.store_order_items USING btree (store_order_id);


--
-- Name: ix_store_orders_fulfillment; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_store_orders_fulfillment ON public.store_orders USING btree (classroom_id, status, created_at);


--
-- Name: ix_store_orders_student_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_store_orders_student_time ON public.store_orders USING btree (classroom_student_id, created_at DESC);


--
-- Name: ix_store_products_classroom_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_store_products_classroom_status ON public.store_products USING btree (classroom_id, status, product_kind);


--
-- Name: ix_student_character_profiles_student; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_student_character_profiles_student ON public.student_character_profiles USING btree (classroom_id, classroom_student_id);


--
-- Name: ix_student_sessions_device; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_student_sessions_device ON public.student_sessions USING btree (device_link_id);


--
-- Name: ix_student_sessions_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_student_sessions_expires ON public.student_sessions USING btree (expires_at) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: ix_subscription_offers_open; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_subscription_offers_open ON public.subscription_offers USING btree (classroom_id, application_closes_at) WHERE ((status)::text = 'OPEN'::text);


--
-- Name: ix_trade_item_legs_inventory; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_trade_item_legs_inventory ON public.trade_item_legs USING btree (inventory_item_id);


--
-- Name: ix_trades_counterpart_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_trades_counterpart_status ON public.trades USING btree (counterpart_student_id, status, created_at DESC);


--
-- Name: ix_trades_proposer_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_trades_proposer_status ON public.trades USING btree (proposer_student_id, status, created_at DESC);


--
-- Name: ix_trades_teacher_queue; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_trades_teacher_queue ON public.trades USING btree (classroom_id, status, created_at) WHERE ((status)::text = 'AWAITING_TEACHER'::text);


--
-- Name: ix_transaction_groups_classroom_committed; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_transaction_groups_classroom_committed ON public.transaction_groups USING btree (classroom_id, committed_at DESC, id) WHERE ((status)::text = 'COMMITTED'::text);


--
-- Name: ix_transaction_groups_recovery; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_transaction_groups_recovery ON public.transaction_groups USING btree (recovery_of_group_id) WHERE (recovery_of_group_id IS NOT NULL);


--
-- Name: ix_transaction_groups_type_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_transaction_groups_type_status ON public.transaction_groups USING btree (classroom_id, transaction_type, status, created_at DESC);


--
-- Name: ix_transaction_inquiries_group; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_transaction_inquiries_group ON public.transaction_inquiries USING btree (transaction_group_id);


--
-- Name: ix_vote_ballots_vote_option; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ix_vote_ballots_vote_option ON public.vote_ballots USING btree (vote_id, vote_option_id);


--
-- Name: uq_ai_decisions_acknowledge; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_ai_decisions_acknowledge ON public.ai_decisions USING btree (ai_proposal_id) WHERE ((decision)::text = 'ACKNOWLEDGE'::text);


--
-- Name: uq_ai_decisions_final; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_ai_decisions_final ON public.ai_decisions USING btree (ai_proposal_id) WHERE ((decision)::text = ANY ((ARRAY['APPROVE'::character varying, 'REJECT'::character varying])::text[]));


--
-- Name: uq_ai_proposals_monitor_dedupe; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_ai_proposals_monitor_dedupe ON public.ai_proposals USING btree (classroom_id, dedupe_key) WHERE (dedupe_key IS NOT NULL);


--
-- Name: uq_quiz_question_issues_student_report; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_quiz_question_issues_student_report ON public.quiz_question_issues USING btree (quiz_session_question_id, reported_by_student_id) WHERE (reported_by_student_id IS NOT NULL);


--
-- Name: uq_regrading_runs_active_session; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_regrading_runs_active_session ON public.regrading_runs USING btree (quiz_session_id) WHERE ((status)::text = ANY ((ARRAY['REQUESTED'::character varying, 'RUNNING'::character varying])::text[]));


--
-- Name: ux_classroom_policy_versions_current; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_classroom_policy_versions_current ON public.classroom_policy_versions USING btree (classroom_id, policy_area) WHERE (superseded_at IS NULL);


--
-- Name: ux_classroom_students_active_nickname; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_classroom_students_active_nickname ON public.classroom_students USING btree (classroom_id, lower((nickname)::text)) WHERE (retired_at IS NULL);


--
-- Name: ux_classroom_students_active_roster; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_classroom_students_active_roster ON public.classroom_students USING btree (classroom_id, roster_number) WHERE (retired_at IS NULL);


--
-- Name: ux_classroom_teachers_active_owner; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_classroom_teachers_active_owner ON public.classroom_teachers USING btree (classroom_id) WHERE (((role)::text = 'OWNER'::text) AND (ended_at IS NULL));


--
-- Name: ux_classroom_teachers_active_role; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_classroom_teachers_active_role ON public.classroom_teachers USING btree (classroom_id, user_id, role) WHERE (ended_at IS NULL);


--
-- Name: ux_financial_products_type_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_financial_products_type_name ON public.financial_products USING btree (classroom_id, product_type, lower((name)::text));


--
-- Name: ux_investment_markets_open; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_investment_markets_open ON public.investment_markets USING btree (classroom_id, investment_mode) WHERE ((status)::text <> 'CLOSED'::text);


--
-- Name: ux_ledger_accounts_active_classroom_fund; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_ledger_accounts_active_classroom_fund ON public.ledger_accounts USING btree (classroom_id, system_owner_key) WHERE (((account_type)::text = 'CLASSROOM_FUND'::text) AND ((status)::text = 'ACTIVE'::text));


--
-- Name: ux_ledger_accounts_active_student_wallet; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_ledger_accounts_active_student_wallet ON public.ledger_accounts USING btree (classroom_student_id) WHERE (((account_type)::text = 'STUDENT_WALLET'::text) AND ((status)::text = 'ACTIVE'::text));


--
-- Name: ux_properties_active_coordinate; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_properties_active_coordinate ON public.properties USING btree (classroom_id, coordinate) WHERE is_active;


--
-- Name: ux_property_sale_listing_active_property; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_property_sale_listing_active_property ON public.property_sale_listings USING btree (property_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: ux_realtime_streams_resource; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_realtime_streams_resource ON public.realtime_streams USING btree (classroom_id, stream_type, COALESCE(resource_id, '00000000-0000-0000-0000-000000000000'::uuid));


--
-- Name: ux_salary_roles_name; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_salary_roles_name ON public.salary_roles USING btree (classroom_id, lower((name)::text));


--
-- Name: ux_salary_roles_role_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_salary_roles_role_code ON public.salary_roles USING btree (classroom_id, role_code);


--
-- Name: ux_school_ai_setting_versions_current; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_school_ai_setting_versions_current ON public.school_ai_setting_versions USING btree (school_id) WHERE (superseded_at IS NULL);


--
-- Name: ux_school_memberships_active_role; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_school_memberships_active_role ON public.school_memberships USING btree (school_id, user_id, role) WHERE (((status)::text = 'ACTIVE'::text) AND (ended_at IS NULL));


--
-- Name: ux_store_products_name_kind; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_store_products_name_kind ON public.store_products USING btree (classroom_id, lower((name)::text), product_kind);


--
-- Name: ux_student_sessions_active_student; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX ux_student_sessions_active_student ON public.student_sessions USING btree (classroom_student_id) WHERE ((status)::text = 'ACTIVE'::text);


--
-- Name: ai_artifacts trg_ai_artifacts_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_ai_artifacts_immutable BEFORE DELETE OR UPDATE ON public.ai_artifacts FOR EACH ROW EXECUTE FUNCTION public.reject_row_mutation();


--
-- Name: audit_events trg_audit_events_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_audit_events_immutable BEFORE DELETE OR UPDATE ON public.audit_events FOR EACH ROW EXECUTE FUNCTION public.reject_row_mutation();


--
-- Name: character_equipment trg_character_equipment_validate_item; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_character_equipment_validate_item BEFORE INSERT OR UPDATE OF inventory_item_id, slot ON public.character_equipment FOR EACH ROW EXECUTE FUNCTION public.validate_character_equipment_item();


--
-- Name: classroom_policy_versions trg_classroom_policy_versions_effective; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_classroom_policy_versions_effective BEFORE DELETE OR UPDATE ON public.classroom_policy_versions FOR EACH ROW EXECUTE FUNCTION public.reject_mutation_when_published('effective_from');


--
-- Name: classroom_teachers trg_classroom_teacher_seed_character_store; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_classroom_teacher_seed_character_store AFTER INSERT OR UPDATE OF ended_at ON public.classroom_teachers FOR EACH ROW EXECUTE FUNCTION public.seed_character_store_for_teacher();


--
-- Name: classrooms trg_classrooms_immutable_identity; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_classrooms_immutable_identity BEFORE UPDATE OF classroom_code, currency_name ON public.classrooms FOR EACH ROW EXECUTE FUNCTION public.prevent_classroom_identity_update();


--
-- Name: course_template_versions trg_course_template_versions_published; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_course_template_versions_published BEFORE DELETE OR UPDATE ON public.course_template_versions FOR EACH ROW EXECUTE FUNCTION public.reject_mutation_when_published('published_at');


--
-- Name: credit_events trg_credit_events_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_credit_events_immutable BEFORE DELETE OR UPDATE ON public.credit_events FOR EACH ROW EXECUTE FUNCTION public.reject_row_mutation();


--
-- Name: financial_product_versions trg_financial_product_versions_published; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_financial_product_versions_published BEFORE DELETE OR UPDATE ON public.financial_product_versions FOR EACH ROW EXECUTE FUNCTION public.reject_mutation_when_published('published_at');


--
-- Name: inventory_items trg_inventory_items_equipment_lock; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_inventory_items_equipment_lock BEFORE UPDATE OF status ON public.inventory_items FOR EACH ROW EXECUTE FUNCTION public.prevent_equipped_inventory_lock();


--
-- Name: investment_scenario_versions trg_investment_scenario_versions_published; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_investment_scenario_versions_published BEFORE DELETE OR UPDATE ON public.investment_scenario_versions FOR EACH ROW EXECUTE FUNCTION public.reject_mutation_when_published('published_at');


--
-- Name: ledger_entries trg_ledger_entries_committed_balance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_ledger_entries_committed_balance AFTER INSERT OR DELETE OR UPDATE ON public.ledger_entries DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.assert_committed_transaction_balanced();


--
-- Name: ledger_entries trg_ledger_entries_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_ledger_entries_immutable BEFORE DELETE OR UPDATE ON public.ledger_entries FOR EACH ROW EXECUTE FUNCTION public.reject_row_mutation();


--
-- Name: loan_policy_versions trg_loan_policy_versions_effective; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_loan_policy_versions_effective BEFORE DELETE OR UPDATE ON public.loan_policy_versions FOR EACH ROW EXECUTE FUNCTION public.reject_mutation_when_published('effective_from');


--
-- Name: outbox_events trg_outbox_events_payload_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_outbox_events_payload_immutable BEFORE DELETE OR UPDATE ON public.outbox_events FOR EACH ROW EXECUTE FUNCTION public.protect_outbox_event();


--
-- Name: policy_snapshot_sources trg_policy_snapshot_sources_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_policy_snapshot_sources_immutable BEFORE DELETE OR UPDATE ON public.policy_snapshot_sources FOR EACH ROW EXECUTE FUNCTION public.reject_row_mutation();


--
-- Name: policy_snapshots trg_policy_snapshots_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_policy_snapshots_immutable BEFORE DELETE OR UPDATE ON public.policy_snapshots FOR EACH ROW EXECUTE FUNCTION public.reject_row_mutation();


--
-- Name: quiz_options trg_quiz_options_correct_answer; Type: TRIGGER; Schema: public; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_quiz_options_correct_answer AFTER INSERT OR DELETE OR UPDATE ON public.quiz_options DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.assert_published_quiz_has_correct_option();


--
-- Name: quiz_question_versions trg_quiz_question_versions_correct_answer; Type: TRIGGER; Schema: public; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_quiz_question_versions_correct_answer AFTER INSERT OR UPDATE OF status ON public.quiz_question_versions DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.assert_published_quiz_has_correct_option();


--
-- Name: quiz_question_versions trg_quiz_question_versions_published; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_quiz_question_versions_published BEFORE DELETE OR UPDATE ON public.quiz_question_versions FOR EACH ROW EXECUTE FUNCTION public.reject_mutation_when_published('published_at');


--
-- Name: quiz_result_versions trg_quiz_result_versions_published; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_quiz_result_versions_published BEFORE DELETE OR UPDATE ON public.quiz_result_versions FOR EACH ROW EXECUTE FUNCTION public.reject_mutation_when_published('published_at');


--
-- Name: quiz_session_questions trg_quiz_session_questions_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_quiz_session_questions_immutable BEFORE DELETE OR UPDATE ON public.quiz_session_questions FOR EACH ROW EXECUTE FUNCTION public.reject_row_mutation();


--
-- Name: quiz_student_results trg_quiz_student_results_published; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_quiz_student_results_published BEFORE DELETE OR UPDATE ON public.quiz_student_results FOR EACH ROW EXECUTE FUNCTION public.protect_published_quiz_student_result();


--
-- Name: report_snapshots trg_report_snapshots_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_report_snapshots_immutable BEFORE DELETE OR UPDATE ON public.report_snapshots FOR EACH ROW EXECUTE FUNCTION public.reject_row_mutation();


--
-- Name: state_transitions trg_state_transitions_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_state_transitions_immutable BEFORE DELETE OR UPDATE ON public.state_transitions FOR EACH ROW EXECUTE FUNCTION public.reject_row_mutation();


--
-- Name: store_product_versions trg_store_product_versions_published; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_store_product_versions_published BEFORE DELETE OR UPDATE ON public.store_product_versions FOR EACH ROW EXECUTE FUNCTION public.reject_mutation_when_published('published_at');


--
-- Name: trade_item_legs trg_trade_item_legs_single_active_trade; Type: TRIGGER; Schema: public; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_trade_item_legs_single_active_trade AFTER INSERT OR DELETE OR UPDATE ON public.trade_item_legs DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.assert_inventory_single_active_trade();


--
-- Name: trades trg_trades_item_lock_consistency; Type: TRIGGER; Schema: public; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_trades_item_lock_consistency AFTER INSERT OR UPDATE OF status ON public.trades DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.assert_inventory_single_active_trade();


--
-- Name: transaction_groups trg_transaction_groups_committed_balance; Type: TRIGGER; Schema: public; Owner: -
--

CREATE CONSTRAINT TRIGGER trg_transaction_groups_committed_balance AFTER INSERT OR UPDATE OF status ON public.transaction_groups DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION public.assert_committed_transaction_balanced();


--
-- Name: transaction_groups trg_transaction_groups_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_transaction_groups_immutable BEFORE DELETE OR UPDATE ON public.transaction_groups FOR EACH ROW EXECUTE FUNCTION public.protect_committed_transaction_group();


--
-- Name: transaction_groups trg_transaction_groups_normalize_recovery; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_transaction_groups_normalize_recovery BEFORE INSERT OR UPDATE OF recovery_of_group_id, root_transaction_group_id ON public.transaction_groups FOR EACH ROW EXECUTE FUNCTION public.normalize_transaction_group_recovery();


--
-- Name: vote_ballots trg_vote_ballots_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_vote_ballots_immutable BEFORE DELETE OR UPDATE ON public.vote_ballots FOR EACH ROW EXECUTE FUNCTION public.reject_row_mutation();


--
-- Name: activity_presentation_slides activity_presentation_slides_activity_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activity_presentation_slides
    ADD CONSTRAINT activity_presentation_slides_activity_session_id_fkey FOREIGN KEY (activity_session_id) REFERENCES public.activity_sessions(id) ON DELETE CASCADE;


--
-- Name: activity_participants fk_activity_participants_session; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activity_participants
    ADD CONSTRAINT fk_activity_participants_session FOREIGN KEY (classroom_id, activity_session_id) REFERENCES public.activity_sessions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: activity_participants fk_activity_participants_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activity_participants
    ADD CONSTRAINT fk_activity_participants_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: activity_sessions fk_activity_sessions_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activity_sessions
    ADD CONSTRAINT fk_activity_sessions_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: activity_sessions fk_activity_sessions_creator; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activity_sessions
    ADD CONSTRAINT fk_activity_sessions_creator FOREIGN KEY (created_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: activity_sessions fk_activity_sessions_policy_snapshot; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.activity_sessions
    ADD CONSTRAINT fk_activity_sessions_policy_snapshot FOREIGN KEY (classroom_id, policy_snapshot_id) REFERENCES public.policy_snapshots(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: ai_artifacts fk_ai_artifacts_run; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_artifacts
    ADD CONSTRAINT fk_ai_artifacts_run FOREIGN KEY (classroom_id, ai_run_id) REFERENCES public.ai_runs(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: ai_decisions fk_ai_decisions_decider; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_decisions
    ADD CONSTRAINT fk_ai_decisions_decider FOREIGN KEY (decided_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: ai_decisions fk_ai_decisions_proposal; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_decisions
    ADD CONSTRAINT fk_ai_decisions_proposal FOREIGN KEY (classroom_id, ai_proposal_id) REFERENCES public.ai_proposals(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: ai_proposals fk_ai_proposals_policy_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_proposals
    ADD CONSTRAINT fk_ai_proposals_policy_version FOREIGN KEY (classroom_id, applied_policy_version_id) REFERENCES public.classroom_policy_versions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: ai_proposals fk_ai_proposals_run; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_proposals
    ADD CONSTRAINT fk_ai_proposals_run FOREIGN KEY (classroom_id, ai_run_id) REFERENCES public.ai_runs(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: ai_runs fk_ai_runs_async_operation; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_runs
    ADD CONSTRAINT fk_ai_runs_async_operation FOREIGN KEY (classroom_id, async_operation_id) REFERENCES public.async_operations(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: ai_runs fk_ai_runs_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_runs
    ADD CONSTRAINT fk_ai_runs_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: ai_runs fk_ai_runs_requester; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_runs
    ADD CONSTRAINT fk_ai_runs_requester FOREIGN KEY (requested_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: async_operations fk_async_operations_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.async_operations
    ADD CONSTRAINT fk_async_operations_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: async_operations fk_async_operations_requester; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.async_operations
    ADD CONSTRAINT fk_async_operations_requester FOREIGN KEY (requested_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: audit_events fk_audit_events_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_events
    ADD CONSTRAINT fk_audit_events_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: audit_events fk_audit_events_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_events
    ADD CONSTRAINT fk_audit_events_group FOREIGN KEY (classroom_id, transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: autonomous_schedule_modules fk_autonomous_schedule_modules_classroom_module; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.autonomous_schedule_modules
    ADD CONSTRAINT fk_autonomous_schedule_modules_classroom_module FOREIGN KEY (classroom_id, module_code) REFERENCES public.classroom_modules(classroom_id, module_code) ON DELETE RESTRICT;


--
-- Name: autonomous_schedule_modules fk_autonomous_schedule_modules_schedule; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.autonomous_schedule_modules
    ADD CONSTRAINT fk_autonomous_schedule_modules_schedule FOREIGN KEY (classroom_id, autonomous_schedule_id) REFERENCES public.autonomous_schedules(classroom_id, id) ON DELETE CASCADE;


--
-- Name: autonomous_schedules fk_autonomous_schedules_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.autonomous_schedules
    ADD CONSTRAINT fk_autonomous_schedules_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: character_equipment fk_character_equipment_owned_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.character_equipment
    ADD CONSTRAINT fk_character_equipment_owned_item FOREIGN KEY (classroom_id, classroom_student_id, inventory_item_id) REFERENCES public.inventory_items(classroom_id, classroom_student_id, id) ON DELETE RESTRICT;


--
-- Name: character_equipment fk_character_equipment_profile; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.character_equipment
    ADD CONSTRAINT fk_character_equipment_profile FOREIGN KEY (classroom_id, classroom_student_id, character_profile_id) REFERENCES public.student_character_profiles(classroom_id, classroom_student_id, id) ON DELETE RESTRICT;


--
-- Name: classroom_characters fk_classroom_characters_catalog; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_characters
    ADD CONSTRAINT fk_classroom_characters_catalog FOREIGN KEY (character_catalog_id) REFERENCES public.character_catalog(id) ON DELETE RESTRICT;


--
-- Name: classroom_characters fk_classroom_characters_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_characters
    ADD CONSTRAINT fk_classroom_characters_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: classroom_characters fk_classroom_characters_product; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_characters
    ADD CONSTRAINT fk_classroom_characters_product FOREIGN KEY (classroom_id, store_product_id) REFERENCES public.store_products(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: classroom_modules fk_classroom_modules_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_modules
    ADD CONSTRAINT fk_classroom_modules_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: classroom_policy_versions fk_classroom_policy_versions_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_policy_versions
    ADD CONSTRAINT fk_classroom_policy_versions_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: classroom_students fk_classroom_students_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_students
    ADD CONSTRAINT fk_classroom_students_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: classroom_students fk_classroom_students_salary_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_students
    ADD CONSTRAINT fk_classroom_students_salary_role FOREIGN KEY (classroom_id, salary_role_id) REFERENCES public.salary_roles(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: classroom_teachers fk_classroom_teachers_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_teachers
    ADD CONSTRAINT fk_classroom_teachers_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: classroom_teachers fk_classroom_teachers_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classroom_teachers
    ADD CONSTRAINT fk_classroom_teachers_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: classrooms fk_classrooms_course_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classrooms
    ADD CONSTRAINT fk_classrooms_course_version FOREIGN KEY (course_template_version_id) REFERENCES public.course_template_versions(id) ON DELETE RESTRICT;


--
-- Name: classrooms fk_classrooms_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.classrooms
    ADD CONSTRAINT fk_classrooms_school FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE RESTRICT;


--
-- Name: course_template_modules fk_course_template_modules_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course_template_modules
    ADD CONSTRAINT fk_course_template_modules_version FOREIGN KEY (course_template_version_id) REFERENCES public.course_template_versions(id) ON DELETE RESTRICT;


--
-- Name: course_template_versions fk_course_template_versions_template; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course_template_versions
    ADD CONSTRAINT fk_course_template_versions_template FOREIGN KEY (course_template_id) REFERENCES public.course_templates(id) ON DELETE RESTRICT;


--
-- Name: credit_events fk_credit_events_profile; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credit_events
    ADD CONSTRAINT fk_credit_events_profile FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.credit_profiles(classroom_id, classroom_student_id) ON DELETE RESTRICT;


--
-- Name: credit_profiles fk_credit_profiles_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.credit_profiles
    ADD CONSTRAINT fk_credit_profiles_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: deposit_accounts fk_deposit_accounts_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deposit_accounts
    ADD CONSTRAINT fk_deposit_accounts_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: deposit_accounts fk_deposit_accounts_closing_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deposit_accounts
    ADD CONSTRAINT fk_deposit_accounts_closing_group FOREIGN KEY (classroom_id, closing_transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: deposit_accounts fk_deposit_accounts_opening_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deposit_accounts
    ADD CONSTRAINT fk_deposit_accounts_opening_group FOREIGN KEY (classroom_id, opening_transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: deposit_accounts fk_deposit_accounts_product; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deposit_accounts
    ADD CONSTRAINT fk_deposit_accounts_product FOREIGN KEY (classroom_id, product_version_id) REFERENCES public.financial_product_versions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: deposit_accounts fk_deposit_accounts_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.deposit_accounts
    ADD CONSTRAINT fk_deposit_accounts_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: device_links fk_device_links_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_links
    ADD CONSTRAINT fk_device_links_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: device_links fk_device_links_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_links
    ADD CONSTRAINT fk_device_links_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: economic_event_drafts fk_economic_event_drafts_activity; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.economic_event_drafts
    ADD CONSTRAINT fk_economic_event_drafts_activity FOREIGN KEY (classroom_id, activity_session_id) REFERENCES public.activity_sessions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: economic_event_drafts fk_economic_event_drafts_ai_run; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.economic_event_drafts
    ADD CONSTRAINT fk_economic_event_drafts_ai_run FOREIGN KEY (classroom_id, ai_run_id) REFERENCES public.ai_runs(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: economic_event_drafts fk_economic_event_drafts_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.economic_event_drafts
    ADD CONSTRAINT fk_economic_event_drafts_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: economic_event_drafts fk_economic_event_drafts_source_proposal; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.economic_event_drafts
    ADD CONSTRAINT fk_economic_event_drafts_source_proposal FOREIGN KEY (classroom_id, source_proposal_id) REFERENCES public.ai_proposals(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: export_operations fk_export_operations_async_operation; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.export_operations
    ADD CONSTRAINT fk_export_operations_async_operation FOREIGN KEY (classroom_id, async_operation_id) REFERENCES public.async_operations(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: export_operations fk_export_operations_requester; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.export_operations
    ADD CONSTRAINT fk_export_operations_requester FOREIGN KEY (requested_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: financial_product_versions fk_financial_product_versions_product; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_product_versions
    ADD CONSTRAINT fk_financial_product_versions_product FOREIGN KEY (classroom_id, financial_product_id) REFERENCES public.financial_products(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: financial_products fk_financial_products_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.financial_products
    ADD CONSTRAINT fk_financial_products_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: idempotency_records fk_idempotency_records_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_records
    ADD CONSTRAINT fk_idempotency_records_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: idempotency_records fk_idempotency_records_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.idempotency_records
    ADD CONSTRAINT fk_idempotency_records_group FOREIGN KEY (classroom_id, transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: inventory_items fk_inventory_items_catalog; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_items
    ADD CONSTRAINT fk_inventory_items_catalog FOREIGN KEY (character_catalog_id) REFERENCES public.character_catalog(id) ON DELETE RESTRICT;


--
-- Name: inventory_items fk_inventory_items_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_items
    ADD CONSTRAINT fk_inventory_items_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: inventory_items fk_inventory_items_locked_trade; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_items
    ADD CONSTRAINT fk_inventory_items_locked_trade FOREIGN KEY (classroom_id, locked_trade_id) REFERENCES public.trades(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: inventory_items fk_inventory_items_source_order_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_items
    ADD CONSTRAINT fk_inventory_items_source_order_item FOREIGN KEY (classroom_id, source_order_item_id) REFERENCES public.store_order_items(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: inventory_items fk_inventory_items_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_items
    ADD CONSTRAINT fk_inventory_items_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: investment_assets fk_investment_assets_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_assets
    ADD CONSTRAINT fk_investment_assets_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: investment_assets fk_investment_assets_scenario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_assets
    ADD CONSTRAINT fk_investment_assets_scenario FOREIGN KEY (classroom_id, scenario_version_id) REFERENCES public.investment_scenario_versions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: investment_markets fk_investment_markets_active_scenario; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_markets
    ADD CONSTRAINT fk_investment_markets_active_scenario FOREIGN KEY (classroom_id, active_scenario_version_id) REFERENCES public.investment_scenario_versions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: investment_markets fk_investment_markets_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_markets
    ADD CONSTRAINT fk_investment_markets_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: investment_orders fk_investment_orders_asset; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_orders
    ADD CONSTRAINT fk_investment_orders_asset FOREIGN KEY (classroom_id, investment_asset_id) REFERENCES public.investment_assets(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: investment_orders fk_investment_orders_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_orders
    ADD CONSTRAINT fk_investment_orders_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: investment_orders fk_investment_orders_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_orders
    ADD CONSTRAINT fk_investment_orders_group FOREIGN KEY (classroom_id, transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: investment_orders fk_investment_orders_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_orders
    ADD CONSTRAINT fk_investment_orders_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: investment_positions fk_investment_positions_asset; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_positions
    ADD CONSTRAINT fk_investment_positions_asset FOREIGN KEY (classroom_id, investment_asset_id) REFERENCES public.investment_assets(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: investment_positions fk_investment_positions_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_positions
    ADD CONSTRAINT fk_investment_positions_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: investment_positions fk_investment_positions_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_positions
    ADD CONSTRAINT fk_investment_positions_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: investment_price_steps fk_investment_price_steps_asset; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_price_steps
    ADD CONSTRAINT fk_investment_price_steps_asset FOREIGN KEY (classroom_id, investment_asset_id) REFERENCES public.investment_assets(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: investment_scenario_versions fk_investment_scenario_versions_market; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.investment_scenario_versions
    ADD CONSTRAINT fk_investment_scenario_versions_market FOREIGN KEY (classroom_id, investment_market_id) REFERENCES public.investment_markets(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: ledger_accounts fk_ledger_accounts_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ledger_accounts
    ADD CONSTRAINT fk_ledger_accounts_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: ledger_accounts fk_ledger_accounts_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ledger_accounts
    ADD CONSTRAINT fk_ledger_accounts_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: ledger_entries fk_ledger_entries_account; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ledger_entries
    ADD CONSTRAINT fk_ledger_entries_account FOREIGN KEY (classroom_id, ledger_account_id) REFERENCES public.ledger_accounts(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: ledger_entries fk_ledger_entries_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ledger_entries
    ADD CONSTRAINT fk_ledger_entries_group FOREIGN KEY (classroom_id, transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: loan_applications fk_loan_applications_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_applications
    ADD CONSTRAINT fk_loan_applications_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: loan_applications fk_loan_applications_decider; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_applications
    ADD CONSTRAINT fk_loan_applications_decider FOREIGN KEY (decided_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: loan_applications fk_loan_applications_policy; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_applications
    ADD CONSTRAINT fk_loan_applications_policy FOREIGN KEY (classroom_id, loan_policy_version_id) REFERENCES public.loan_policy_versions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: loan_applications fk_loan_applications_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_applications
    ADD CONSTRAINT fk_loan_applications_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: loan_installments fk_loan_installments_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_installments
    ADD CONSTRAINT fk_loan_installments_group FOREIGN KEY (classroom_id, transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: loan_installments fk_loan_installments_loan; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_installments
    ADD CONSTRAINT fk_loan_installments_loan FOREIGN KEY (classroom_id, loan_id) REFERENCES public.loans(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: loan_policy_versions fk_loan_policy_versions_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loan_policy_versions
    ADD CONSTRAINT fk_loan_policy_versions_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: loans fk_loans_application; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loans
    ADD CONSTRAINT fk_loans_application FOREIGN KEY (classroom_id, loan_application_id) REFERENCES public.loan_applications(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: loans fk_loans_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loans
    ADD CONSTRAINT fk_loans_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: loans fk_loans_disbursement_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.loans
    ADD CONSTRAINT fk_loans_disbursement_group FOREIGN KEY (classroom_id, disbursement_transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: outbox_events fk_outbox_events_stream; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outbox_events
    ADD CONSTRAINT fk_outbox_events_stream FOREIGN KEY (classroom_id, realtime_stream_id) REFERENCES public.realtime_streams(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: payslips fk_payslips_policy_snapshot; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payslips
    ADD CONSTRAINT fk_payslips_policy_snapshot FOREIGN KEY (classroom_id, policy_snapshot_id) REFERENCES public.policy_snapshots(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: payslips fk_payslips_salary_role; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payslips
    ADD CONSTRAINT fk_payslips_salary_role FOREIGN KEY (classroom_id, salary_role_id) REFERENCES public.salary_roles(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: payslips fk_payslips_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payslips
    ADD CONSTRAINT fk_payslips_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: payslips fk_payslips_transaction_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payslips
    ADD CONSTRAINT fk_payslips_transaction_group FOREIGN KEY (classroom_id, transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: policy_snapshot_sources fk_policy_snapshot_sources_snapshot; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.policy_snapshot_sources
    ADD CONSTRAINT fk_policy_snapshot_sources_snapshot FOREIGN KEY (classroom_id, policy_snapshot_id) REFERENCES public.policy_snapshots(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: policy_snapshots fk_policy_snapshots_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.policy_snapshots
    ADD CONSTRAINT fk_policy_snapshots_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: properties fk_properties_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.properties
    ADD CONSTRAINT fk_properties_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: properties fk_properties_owner_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.properties
    ADD CONSTRAINT fk_properties_owner_student FOREIGN KEY (classroom_id, owner_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: properties fk_properties_subscription_offer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.properties
    ADD CONSTRAINT fk_properties_subscription_offer FOREIGN KEY (classroom_id, subscription_offer_id) REFERENCES public.subscription_offers(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: property_contract_payments fk_property_contract_payments_contract; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contract_payments
    ADD CONSTRAINT fk_property_contract_payments_contract FOREIGN KEY (classroom_id, property_contract_id) REFERENCES public.property_contracts(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: property_contract_payments fk_property_contract_payments_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contract_payments
    ADD CONSTRAINT fk_property_contract_payments_group FOREIGN KEY (classroom_id, transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: property_contracts fk_property_contracts_approver; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contracts
    ADD CONSTRAINT fk_property_contracts_approver FOREIGN KEY (approved_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: property_contracts fk_property_contracts_landlord; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contracts
    ADD CONSTRAINT fk_property_contracts_landlord FOREIGN KEY (classroom_id, landlord_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: property_contracts fk_property_contracts_listing; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contracts
    ADD CONSTRAINT fk_property_contracts_listing FOREIGN KEY (classroom_id, lease_listing_id) REFERENCES public.property_lease_listings(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: property_contracts fk_property_contracts_occupant; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contracts
    ADD CONSTRAINT fk_property_contracts_occupant FOREIGN KEY (classroom_id, occupant_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: property_contracts fk_property_contracts_offer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contracts
    ADD CONSTRAINT fk_property_contracts_offer FOREIGN KEY (classroom_id, subscription_offer_id) REFERENCES public.subscription_offers(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: property_contracts fk_property_contracts_property; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_contracts
    ADD CONSTRAINT fk_property_contracts_property FOREIGN KEY (classroom_id, property_id) REFERENCES public.properties(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: property_layouts fk_property_layout_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_layouts
    ADD CONSTRAINT fk_property_layout_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: property_lease_listings fk_property_lease_listings_landlord; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_lease_listings
    ADD CONSTRAINT fk_property_lease_listings_landlord FOREIGN KEY (classroom_id, landlord_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: property_lease_listings fk_property_lease_listings_property; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_lease_listings
    ADD CONSTRAINT fk_property_lease_listings_property FOREIGN KEY (classroom_id, property_id) REFERENCES public.properties(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: property_sale_listings fk_property_sale_listings_property; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_sale_listings
    ADD CONSTRAINT fk_property_sale_listings_property FOREIGN KEY (classroom_id, property_id) REFERENCES public.properties(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: property_sale_listings fk_property_sale_listings_seller; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.property_sale_listings
    ADD CONSTRAINT fk_property_sale_listings_seller FOREIGN KEY (classroom_id, seller_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: quiz_answers fk_quiz_answers_session_question; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_answers
    ADD CONSTRAINT fk_quiz_answers_session_question FOREIGN KEY (classroom_id, quiz_session_question_id) REFERENCES public.quiz_session_questions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: quiz_answers fk_quiz_answers_submission; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_answers
    ADD CONSTRAINT fk_quiz_answers_submission FOREIGN KEY (classroom_id, quiz_submission_id) REFERENCES public.quiz_submissions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: quiz_options fk_quiz_options_question_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_options
    ADD CONSTRAINT fk_quiz_options_question_version FOREIGN KEY (classroom_id, quiz_question_version_id) REFERENCES public.quiz_question_versions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: quiz_question_issues fk_quiz_question_issues_reporter; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_question_issues
    ADD CONSTRAINT fk_quiz_question_issues_reporter FOREIGN KEY (classroom_id, reported_by_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: quiz_question_issues fk_quiz_question_issues_resolver; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_question_issues
    ADD CONSTRAINT fk_quiz_question_issues_resolver FOREIGN KEY (resolved_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: quiz_question_issues fk_quiz_question_issues_session_question; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_question_issues
    ADD CONSTRAINT fk_quiz_question_issues_session_question FOREIGN KEY (classroom_id, quiz_session_question_id) REFERENCES public.quiz_session_questions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: quiz_question_versions fk_quiz_question_versions_ai_run; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_question_versions
    ADD CONSTRAINT fk_quiz_question_versions_ai_run FOREIGN KEY (classroom_id, ai_run_id) REFERENCES public.ai_runs(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: quiz_question_versions fk_quiz_question_versions_question; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_question_versions
    ADD CONSTRAINT fk_quiz_question_versions_question FOREIGN KEY (classroom_id, quiz_question_id) REFERENCES public.quiz_questions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: quiz_questions fk_quiz_questions_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_questions
    ADD CONSTRAINT fk_quiz_questions_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: quiz_questions fk_quiz_questions_creator; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_questions
    ADD CONSTRAINT fk_quiz_questions_creator FOREIGN KEY (created_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: quiz_result_versions fk_quiz_result_versions_reward_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_result_versions
    ADD CONSTRAINT fk_quiz_result_versions_reward_group FOREIGN KEY (classroom_id, reward_transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: quiz_result_versions fk_quiz_result_versions_session; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_result_versions
    ADD CONSTRAINT fk_quiz_result_versions_session FOREIGN KEY (classroom_id, quiz_session_id) REFERENCES public.quiz_sessions(classroom_id, activity_session_id) ON DELETE RESTRICT;


--
-- Name: quiz_result_versions fk_quiz_result_versions_supersedes; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_result_versions
    ADD CONSTRAINT fk_quiz_result_versions_supersedes FOREIGN KEY (classroom_id, supersedes_result_version_id) REFERENCES public.quiz_result_versions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: quiz_session_questions fk_quiz_session_questions_question_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_session_questions
    ADD CONSTRAINT fk_quiz_session_questions_question_version FOREIGN KEY (classroom_id, quiz_question_version_id) REFERENCES public.quiz_question_versions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: quiz_session_questions fk_quiz_session_questions_session; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_session_questions
    ADD CONSTRAINT fk_quiz_session_questions_session FOREIGN KEY (classroom_id, quiz_session_id) REFERENCES public.quiz_sessions(classroom_id, activity_session_id) ON DELETE RESTRICT;


--
-- Name: quiz_sessions fk_quiz_sessions_activity; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_sessions
    ADD CONSTRAINT fk_quiz_sessions_activity FOREIGN KEY (classroom_id, activity_session_id) REFERENCES public.activity_sessions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: quiz_student_results fk_quiz_student_results_result_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_student_results
    ADD CONSTRAINT fk_quiz_student_results_result_version FOREIGN KEY (classroom_id, quiz_result_version_id) REFERENCES public.quiz_result_versions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: quiz_student_results fk_quiz_student_results_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_student_results
    ADD CONSTRAINT fk_quiz_student_results_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: quiz_submissions fk_quiz_submissions_session; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_submissions
    ADD CONSTRAINT fk_quiz_submissions_session FOREIGN KEY (classroom_id, quiz_session_id) REFERENCES public.quiz_sessions(classroom_id, activity_session_id) ON DELETE RESTRICT;


--
-- Name: quiz_submissions fk_quiz_submissions_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quiz_submissions
    ADD CONSTRAINT fk_quiz_submissions_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: realtime_streams fk_realtime_streams_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.realtime_streams
    ADD CONSTRAINT fk_realtime_streams_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: regrading_runs fk_regrading_runs_new_result; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regrading_runs
    ADD CONSTRAINT fk_regrading_runs_new_result FOREIGN KEY (classroom_id, new_result_version_id) REFERENCES public.quiz_result_versions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: regrading_runs fk_regrading_runs_session; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regrading_runs
    ADD CONSTRAINT fk_regrading_runs_session FOREIGN KEY (classroom_id, quiz_session_id) REFERENCES public.quiz_sessions(classroom_id, activity_session_id) ON DELETE RESTRICT;


--
-- Name: regrading_runs fk_regrading_runs_source_result; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regrading_runs
    ADD CONSTRAINT fk_regrading_runs_source_result FOREIGN KEY (classroom_id, source_result_version_id) REFERENCES public.quiz_result_versions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: regrading_runs fk_regrading_runs_starter; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.regrading_runs
    ADD CONSTRAINT fk_regrading_runs_starter FOREIGN KEY (started_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: report_snapshots fk_report_snapshots_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_snapshots
    ADD CONSTRAINT fk_report_snapshots_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: report_snapshots fk_report_snapshots_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.report_snapshots
    ADD CONSTRAINT fk_report_snapshots_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: salary_roles fk_salary_roles_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salary_roles
    ADD CONSTRAINT fk_salary_roles_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: savings_accounts fk_savings_accounts_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.savings_accounts
    ADD CONSTRAINT fk_savings_accounts_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: savings_accounts fk_savings_accounts_closing_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.savings_accounts
    ADD CONSTRAINT fk_savings_accounts_closing_group FOREIGN KEY (classroom_id, closing_transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: savings_accounts fk_savings_accounts_opening_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.savings_accounts
    ADD CONSTRAINT fk_savings_accounts_opening_group FOREIGN KEY (classroom_id, opening_transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: savings_accounts fk_savings_accounts_product; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.savings_accounts
    ADD CONSTRAINT fk_savings_accounts_product FOREIGN KEY (classroom_id, product_version_id) REFERENCES public.financial_product_versions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: savings_accounts fk_savings_accounts_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.savings_accounts
    ADD CONSTRAINT fk_savings_accounts_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: savings_installments fk_savings_installments_account; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.savings_installments
    ADD CONSTRAINT fk_savings_installments_account FOREIGN KEY (classroom_id, savings_account_id) REFERENCES public.savings_accounts(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: savings_installments fk_savings_installments_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.savings_installments
    ADD CONSTRAINT fk_savings_installments_group FOREIGN KEY (classroom_id, transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: school_ai_setting_versions fk_school_ai_setting_versions_creator; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_ai_setting_versions
    ADD CONSTRAINT fk_school_ai_setting_versions_creator FOREIGN KEY (created_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: school_ai_setting_versions fk_school_ai_setting_versions_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_ai_setting_versions
    ADD CONSTRAINT fk_school_ai_setting_versions_school FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE RESTRICT;


--
-- Name: school_memberships fk_school_memberships_school; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_memberships
    ADD CONSTRAINT fk_school_memberships_school FOREIGN KEY (school_id) REFERENCES public.schools(id) ON DELETE RESTRICT;


--
-- Name: school_memberships fk_school_memberships_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.school_memberships
    ADD CONSTRAINT fk_school_memberships_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: state_transitions fk_state_transitions_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.state_transitions
    ADD CONSTRAINT fk_state_transitions_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: state_transitions fk_state_transitions_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.state_transitions
    ADD CONSTRAINT fk_state_transitions_group FOREIGN KEY (classroom_id, transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: store_order_items fk_store_order_items_order; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_order_items
    ADD CONSTRAINT fk_store_order_items_order FOREIGN KEY (classroom_id, store_order_id) REFERENCES public.store_orders(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: store_order_items fk_store_order_items_product_version; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_order_items
    ADD CONSTRAINT fk_store_order_items_product_version FOREIGN KEY (classroom_id, store_product_version_id) REFERENCES public.store_product_versions(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: store_orders fk_store_orders_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_orders
    ADD CONSTRAINT fk_store_orders_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: store_orders fk_store_orders_policy_snapshot; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_orders
    ADD CONSTRAINT fk_store_orders_policy_snapshot FOREIGN KEY (classroom_id, policy_snapshot_id) REFERENCES public.policy_snapshots(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: store_orders fk_store_orders_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_orders
    ADD CONSTRAINT fk_store_orders_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: store_orders fk_store_orders_transaction_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_orders
    ADD CONSTRAINT fk_store_orders_transaction_group FOREIGN KEY (classroom_id, transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: store_product_versions fk_store_product_versions_product; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_product_versions
    ADD CONSTRAINT fk_store_product_versions_product FOREIGN KEY (classroom_id, store_product_id) REFERENCES public.store_products(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: store_products fk_store_products_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_products
    ADD CONSTRAINT fk_store_products_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: store_products fk_store_products_creator; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_products
    ADD CONSTRAINT fk_store_products_creator FOREIGN KEY (created_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: store_stocks fk_store_stocks_product; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_stocks
    ADD CONSTRAINT fk_store_stocks_product FOREIGN KEY (classroom_id, store_product_id) REFERENCES public.store_products(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: student_character_profiles fk_student_character_profiles_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_character_profiles
    ADD CONSTRAINT fk_student_character_profiles_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: student_credentials fk_student_credentials_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_credentials
    ADD CONSTRAINT fk_student_credentials_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: student_sessions fk_student_sessions_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_sessions
    ADD CONSTRAINT fk_student_sessions_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: student_sessions fk_student_sessions_device; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_sessions
    ADD CONSTRAINT fk_student_sessions_device FOREIGN KEY (classroom_id, device_link_id) REFERENCES public.device_links(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: student_sessions fk_student_sessions_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_sessions
    ADD CONSTRAINT fk_student_sessions_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: subscription_account_deposits fk_subscription_account_deposits_account; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_account_deposits
    ADD CONSTRAINT fk_subscription_account_deposits_account FOREIGN KEY (classroom_student_id) REFERENCES public.subscription_accounts(classroom_student_id) ON DELETE RESTRICT;


--
-- Name: subscription_account_deposits fk_subscription_account_deposits_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_account_deposits
    ADD CONSTRAINT fk_subscription_account_deposits_group FOREIGN KEY (classroom_id, transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: subscription_accounts fk_subscription_accounts_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_accounts
    ADD CONSTRAINT fk_subscription_accounts_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: subscription_applications fk_subscription_applications_offer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_applications
    ADD CONSTRAINT fk_subscription_applications_offer FOREIGN KEY (classroom_id, subscription_offer_id) REFERENCES public.subscription_offers(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: subscription_applications fk_subscription_applications_property; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_applications
    ADD CONSTRAINT fk_subscription_applications_property FOREIGN KEY (classroom_id, assigned_property_id) REFERENCES public.properties(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: subscription_applications fk_subscription_applications_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_applications
    ADD CONSTRAINT fk_subscription_applications_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: subscription_auto_deposit_policies fk_subscription_auto_deposit_policies_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_auto_deposit_policies
    ADD CONSTRAINT fk_subscription_auto_deposit_policies_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: subscription_offer_properties fk_subscription_offer_properties_offer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_offer_properties
    ADD CONSTRAINT fk_subscription_offer_properties_offer FOREIGN KEY (classroom_id, subscription_offer_id) REFERENCES public.subscription_offers(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: subscription_offer_properties fk_subscription_offer_properties_property; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_offer_properties
    ADD CONSTRAINT fk_subscription_offer_properties_property FOREIGN KEY (classroom_id, property_id) REFERENCES public.properties(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: subscription_offers fk_subscription_offers_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_offers
    ADD CONSTRAINT fk_subscription_offers_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: subscription_offers fk_subscription_offers_drawer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.subscription_offers
    ADD CONSTRAINT fk_subscription_offers_drawer FOREIGN KEY (drawn_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: teacher_credentials fk_teacher_credentials_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teacher_credentials
    ADD CONSTRAINT fk_teacher_credentials_user FOREIGN KEY (teacher_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: trade_decisions fk_trade_decisions_trade; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_decisions
    ADD CONSTRAINT fk_trade_decisions_trade FOREIGN KEY (classroom_id, trade_id) REFERENCES public.trades(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: trade_item_legs fk_trade_item_legs_from_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_item_legs
    ADD CONSTRAINT fk_trade_item_legs_from_student FOREIGN KEY (classroom_id, from_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: trade_item_legs fk_trade_item_legs_inventory_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_item_legs
    ADD CONSTRAINT fk_trade_item_legs_inventory_item FOREIGN KEY (classroom_id, inventory_item_id) REFERENCES public.inventory_items(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: trade_item_legs fk_trade_item_legs_to_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_item_legs
    ADD CONSTRAINT fk_trade_item_legs_to_student FOREIGN KEY (classroom_id, to_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: trade_item_legs fk_trade_item_legs_trade; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_item_legs
    ADD CONSTRAINT fk_trade_item_legs_trade FOREIGN KEY (classroom_id, trade_id) REFERENCES public.trades(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: trade_money_legs fk_trade_money_legs_from_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_money_legs
    ADD CONSTRAINT fk_trade_money_legs_from_student FOREIGN KEY (classroom_id, from_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: trade_money_legs fk_trade_money_legs_to_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_money_legs
    ADD CONSTRAINT fk_trade_money_legs_to_student FOREIGN KEY (classroom_id, to_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: trade_money_legs fk_trade_money_legs_trade; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trade_money_legs
    ADD CONSTRAINT fk_trade_money_legs_trade FOREIGN KEY (classroom_id, trade_id) REFERENCES public.trades(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: trades fk_trades_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trades
    ADD CONSTRAINT fk_trades_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: trades fk_trades_counterpart; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trades
    ADD CONSTRAINT fk_trades_counterpart FOREIGN KEY (classroom_id, counterpart_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: trades fk_trades_policy_snapshot; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trades
    ADD CONSTRAINT fk_trades_policy_snapshot FOREIGN KEY (classroom_id, policy_snapshot_id) REFERENCES public.policy_snapshots(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: trades fk_trades_proposer; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trades
    ADD CONSTRAINT fk_trades_proposer FOREIGN KEY (classroom_id, proposer_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: trades fk_trades_transaction_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.trades
    ADD CONSTRAINT fk_trades_transaction_group FOREIGN KEY (classroom_id, transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: transaction_business_refs fk_transaction_business_refs_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_business_refs
    ADD CONSTRAINT fk_transaction_business_refs_group FOREIGN KEY (classroom_id, transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: transaction_groups fk_transaction_groups_classroom; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_groups
    ADD CONSTRAINT fk_transaction_groups_classroom FOREIGN KEY (classroom_id) REFERENCES public.classrooms(id) ON DELETE RESTRICT;


--
-- Name: transaction_groups fk_transaction_groups_policy_snapshot; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_groups
    ADD CONSTRAINT fk_transaction_groups_policy_snapshot FOREIGN KEY (classroom_id, policy_snapshot_id) REFERENCES public.policy_snapshots(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: transaction_groups fk_transaction_groups_recovery; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_groups
    ADD CONSTRAINT fk_transaction_groups_recovery FOREIGN KEY (classroom_id, recovery_of_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: transaction_groups fk_transaction_groups_root; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_groups
    ADD CONSTRAINT fk_transaction_groups_root FOREIGN KEY (classroom_id, root_transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: transaction_inquiries fk_transaction_inquiries_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_inquiries
    ADD CONSTRAINT fk_transaction_inquiries_group FOREIGN KEY (classroom_id, transaction_group_id) REFERENCES public.transaction_groups(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: transaction_inquiries fk_transaction_inquiries_resolver; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_inquiries
    ADD CONSTRAINT fk_transaction_inquiries_resolver FOREIGN KEY (resolved_by_user_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: transaction_inquiries fk_transaction_inquiries_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.transaction_inquiries
    ADD CONSTRAINT fk_transaction_inquiries_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: vote_ballots fk_vote_ballots_eligibility; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_ballots
    ADD CONSTRAINT fk_vote_ballots_eligibility FOREIGN KEY (classroom_id, vote_id, eligibility_token_hash) REFERENCES public.vote_eligibilities(classroom_id, vote_id, eligibility_token_hash) ON DELETE RESTRICT;


--
-- Name: vote_ballots fk_vote_ballots_option; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_ballots
    ADD CONSTRAINT fk_vote_ballots_option FOREIGN KEY (classroom_id, vote_option_id) REFERENCES public.vote_options(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: vote_ballots fk_vote_ballots_vote; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_ballots
    ADD CONSTRAINT fk_vote_ballots_vote FOREIGN KEY (classroom_id, vote_id) REFERENCES public.votes(classroom_id, activity_session_id) ON DELETE RESTRICT;


--
-- Name: vote_ballots fk_vote_ballots_vote_option; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_ballots
    ADD CONSTRAINT fk_vote_ballots_vote_option FOREIGN KEY (classroom_id, vote_id, vote_option_id) REFERENCES public.vote_options(classroom_id, vote_id, id) ON DELETE RESTRICT;


--
-- Name: vote_eligibilities fk_vote_eligibilities_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_eligibilities
    ADD CONSTRAINT fk_vote_eligibilities_student FOREIGN KEY (classroom_id, classroom_student_id) REFERENCES public.classroom_students(classroom_id, id) ON DELETE RESTRICT;


--
-- Name: vote_eligibilities fk_vote_eligibilities_vote; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_eligibilities
    ADD CONSTRAINT fk_vote_eligibilities_vote FOREIGN KEY (classroom_id, vote_id) REFERENCES public.votes(classroom_id, activity_session_id) ON DELETE RESTRICT;


--
-- Name: vote_options fk_vote_options_vote; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vote_options
    ADD CONSTRAINT fk_vote_options_vote FOREIGN KEY (classroom_id, vote_id) REFERENCES public.votes(classroom_id, activity_session_id) ON DELETE RESTRICT;


--
-- Name: votes fk_votes_activity; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.votes
    ADD CONSTRAINT fk_votes_activity FOREIGN KEY (classroom_id, activity_session_id) REFERENCES public.activity_sessions(classroom_id, id) ON DELETE RESTRICT;


--
-- PostgreSQL database dump complete
--

\unrestrict DVy72K4IfepDaSMAgfIZ6Eexj13EnPRJcH95rwQRym0mMGnGaVdXe5RN7EsCihG
