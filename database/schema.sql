-- OBMS - MySQL schema
--
-- GENERATED FILE. Do not edit by hand.
--
-- Produced by the mysql-schema CI job: the Spring Boot
-- application boots against MySQL 8.4, Hibernate creates the
-- schema from the JPA entity mappings, and the result is
-- dumped here. Editing an entity regenerates this file, so it
-- cannot drift from the code.
--
-- Apply with:  mysql -u obms_user -p obms < database/schema.sql

SET FOREIGN_KEY_CHECKS = 0;


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `accounts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `accounts` (
  `balance` decimal(19,2) NOT NULL,
  `currency` varchar(3) NOT NULL,
  `daily_transfer_limit` decimal(19,2) NOT NULL,
  `minimum_balance` decimal(19,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `customer_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL,
  `ifsc_code` varchar(11) NOT NULL,
  `account_number` varchar(20) NOT NULL,
  `nickname` varchar(60) DEFAULT NULL,
  `account_type` enum('CURRENT','SAVINGS') NOT NULL,
  `status` enum('ACTIVE','CLOSED','DORMANT','FROZEN') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_account_number` (`account_number`),
  KEY `idx_account_customer` (`customer_id`),
  CONSTRAINT `FKn6x8pdp50os8bq5rbb792upse` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_logs` (
  `success` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `entity_type` varchar(60) DEFAULT NULL,
  `entity_ref` varchar(64) DEFAULT NULL,
  `action` varchar(80) NOT NULL,
  `actor` varchar(160) NOT NULL,
  `detail` varchar(500) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_actor` (`actor`,`created_at`),
  KEY `idx_audit_action` (`action`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `beneficiaries`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `beneficiaries` (
  `internal_account` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `owner_customer_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `verified_at` datetime(6) DEFAULT NULL,
  `ifsc_code` varchar(11) NOT NULL,
  `account_number` varchar(20) NOT NULL,
  `nickname` varchar(60) DEFAULT NULL,
  `bank_name` varchar(120) DEFAULT NULL,
  `beneficiary_name` varchar(120) NOT NULL,
  `status` enum('ACTIVE','PENDING_VERIFICATION','REMOVED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_beneficiary_owner_account` (`owner_customer_id`,`account_number`),
  KEY `idx_beneficiary_owner` (`owner_customer_id`),
  CONSTRAINT `FKr5xf30pn6mtq5ukn7g6bdhllf` FOREIGN KEY (`owner_customer_id`) REFERENCES `customers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `bill_payments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bill_payments` (
  `amount` decimal(19,2) NOT NULL,
  `due_date` date DEFAULT NULL,
  `account_id` bigint NOT NULL,
  `biller_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `customer_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `paid_at` datetime(6) DEFAULT NULL,
  `transaction_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `reference` varchar(32) NOT NULL,
  `consumer_identifier` varchar(60) NOT NULL,
  `failure_reason` varchar(255) DEFAULT NULL,
  `status` enum('AWAITING_VERIFICATION','BLOCKED','COMPLETED','FAILED','PENDING','REVERSED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_billpay_reference` (`reference`),
  UNIQUE KEY `UKjqd9k8j95odbef5o7v6lmscap` (`transaction_id`),
  KEY `idx_billpay_customer` (`customer_id`),
  KEY `FKh09cns3fmk7j4v3h23ahfwjpw` (`account_id`),
  KEY `FKiv6xu93i046itb5iun1h6q5px` (`biller_id`),
  CONSTRAINT `FKh09cns3fmk7j4v3h23ahfwjpw` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`),
  CONSTRAINT `FKiv6xu93i046itb5iun1h6q5px` FOREIGN KEY (`biller_id`) REFERENCES `billers` (`id`),
  CONSTRAINT `FKn70p5mnr0drbavokrqhd4kdxu` FOREIGN KEY (`transaction_id`) REFERENCES `transactions` (`id`),
  CONSTRAINT `FKskdcthur2n5h93jmcpmymgc9` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `billers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `billers` (
  `active` bit(1) NOT NULL,
  `max_amount` decimal(19,2) DEFAULT NULL,
  `min_amount` decimal(19,2) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `code` varchar(20) NOT NULL,
  `identifier_label` varchar(60) NOT NULL,
  `name` varchar(120) NOT NULL,
  `category` enum('BROADBAND','CREDIT_CARD','ELECTRICITY','GAS','MOBILE_RECHARGE','WATER') NOT NULL,
  `spend_category` enum('EDUCATION','ENTERTAINMENT','FOOD','HEALTHCARE','INCOME','OTHER','RENT','SAVINGS','SHOPPING','TRANSFER','TRANSPORT','UTILITIES') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK61h58u3bujlmt576js20ib9tn` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `copilot_conversations`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `copilot_conversations` (
  `created_at` datetime(6) NOT NULL,
  `customer_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `title` varchar(160) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_convo_customer` (`customer_id`,`created_at`),
  CONSTRAINT `FK6yve2g9ac28vccpum7kdolxgk` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `copilot_messages`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `copilot_messages` (
  `conversation_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `generator` varchar(20) DEFAULT NULL,
  `intent` varchar(40) DEFAULT NULL,
  `content` text NOT NULL,
  `data_json` text,
  `role` enum('ASSISTANT','USER') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6sa7nsscsw2qdcclo75n1mpqx` (`conversation_id`),
  CONSTRAINT `FK6sa7nsscsw2qdcclo75n1mpqx` FOREIGN KEY (`conversation_id`) REFERENCES `copilot_conversations` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `customers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customers` (
  `date_of_birth` date DEFAULT NULL,
  `declared_monthly_income` decimal(19,2) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `pincode` varchar(10) DEFAULT NULL,
  `mobile` varchar(15) NOT NULL,
  `customer_number` varchar(20) NOT NULL,
  `city` varchar(60) DEFAULT NULL,
  `first_name` varchar(60) NOT NULL,
  `last_name` varchar(60) NOT NULL,
  `state` varchar(60) DEFAULT NULL,
  `email` varchar(160) NOT NULL,
  `address_line` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_customer_email` (`email`),
  UNIQUE KEY `idx_customer_mobile` (`mobile`),
  UNIQUE KEY `idx_customer_number` (`customer_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `financial_score_snapshots`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `financial_score_snapshots` (
  `score` int NOT NULL,
  `period` varchar(7) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `customer_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `band` varchar(20) NOT NULL,
  `formula_version` varchar(20) DEFAULT NULL,
  `breakdown_json` text,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_score_customer_period` (`customer_id`,`period`),
  KEY `idx_score_customer` (`customer_id`),
  CONSTRAINT `FKcrjkmh9ussur8xeeqjmwqutj7` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `fraud_alerts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fraud_alerts` (
  `amount` decimal(19,2) NOT NULL,
  `marked_false_positive` bit(1) NOT NULL,
  `risk_score` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `customer_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `transaction_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `decision` varchar(20) NOT NULL,
  `evaluated_ref` varchar(32) DEFAULT NULL,
  `model_version` varchar(40) DEFAULT NULL,
  `reviewed_by` varchar(160) DEFAULT NULL,
  `review_notes` varchar(500) DEFAULT NULL,
  `primary_reason` varchar(255) NOT NULL,
  `explanation_json` text,
  `risk_level` enum('CRITICAL','HIGH','LOW','MEDIUM') NOT NULL,
  `status` enum('CLOSED','CONFIRMED_FRAUD','FALSE_POSITIVE','OPEN','UNDER_REVIEW') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_fraud_customer` (`customer_id`,`created_at`),
  KEY `idx_fraud_status` (`status`),
  KEY `idx_fraud_level` (`risk_level`),
  KEY `FKqci12ibsyesvy4l6uq89si9io` (`transaction_id`),
  CONSTRAINT `FKf29jrjhcw1n5wh0fpibr42imq` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`),
  CONSTRAINT `FKqci12ibsyesvy4l6uq89si9io` FOREIGN KEY (`transaction_id`) REFERENCES `transactions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `idempotency_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `idempotency_records` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `result_ref` varchar(32) DEFAULT NULL,
  `operation` varchar(40) NOT NULL,
  `idempotency_key` varchar(120) NOT NULL,
  `username` varchar(160) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_idempotency_key` (`idempotency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `kyc_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kyc_records` (
  `created_at` datetime(6) NOT NULL,
  `customer_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reviewed_at` datetime(6) DEFAULT NULL,
  `submitted_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `address_proof_type` varchar(40) DEFAULT NULL,
  `id_proof_masked` varchar(40) DEFAULT NULL,
  `id_proof_type` varchar(40) DEFAULT NULL,
  `reviewed_by` varchar(160) DEFAULT NULL,
  `rejection_reason` varchar(255) DEFAULT NULL,
  `status` enum('NOT_SUBMITTED','PENDING','REJECTED','VERIFIED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKd4fagm1p4j25y11swxie6axp8` (`customer_id`),
  CONSTRAINT `FK95wk8b370gyd9cytedm6mny96` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `notifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notifications` (
  `dispatched` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `customer_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `read_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `title` varchar(140) NOT NULL,
  `link_target` varchar(160) DEFAULT NULL,
  `body` varchar(500) NOT NULL,
  `channel` enum('EMAIL','IN_APP','SMS') NOT NULL,
  `notification_type` enum('BENEFICIARY_ADDED','BILL_REMINDER','LOGIN_ALERT','SAVINGS_GOAL','SECURITY','SUSPICIOUS_ACTIVITY','TRANSACTION_FAILED','TRANSACTION_SUCCESS') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notification_customer` (`customer_id`,`read_at`),
  CONSTRAINT `FK30dp6ycner3dgso3scgc9vghy` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `otp_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `otp_tokens` (
  `attempts` int NOT NULL,
  `max_attempts` int NOT NULL,
  `consumed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `target_ref` varchar(64) DEFAULT NULL,
  `code_hash` varchar(100) NOT NULL,
  `username` varchar(160) NOT NULL,
  `purpose` enum('BENEFICIARY_VERIFICATION','LOGIN','PASSWORD_RESET','PROFILE_UPDATE','TRANSFER') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_otp_lookup` (`username`,`purpose`,`consumed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `pending_transfers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pending_transfers` (
  `amount` decimal(19,2) NOT NULL,
  `risk_score` int DEFAULT NULL,
  `beneficiary_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `customer_id` bigint NOT NULL,
  `destination_account_id` bigint DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `source_account_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `risk_decision` varchar(20) DEFAULT NULL,
  `pending_ref` varchar(32) NOT NULL,
  `resulting_reference` varchar(32) DEFAULT NULL,
  `idempotency_key` varchar(120) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `risk_explanation_json` text,
  `risk_level` enum('CRITICAL','HIGH','LOW','MEDIUM') DEFAULT NULL,
  `status` enum('AWAITING_VERIFICATION','BLOCKED','COMPLETED','FAILED','PENDING','REVERSED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_pending_ref` (`pending_ref`),
  KEY `idx_pending_customer` (`customer_id`,`status`),
  KEY `FK5m24o4ukumw4nsclwmh9blu00` (`beneficiary_id`),
  KEY `FKlms9dy5pt8av8dtj6v5k0d5vk` (`destination_account_id`),
  KEY `FKl2ekkqisi0d84xky5y9mkonun` (`source_account_id`),
  CONSTRAINT `FK5m24o4ukumw4nsclwmh9blu00` FOREIGN KEY (`beneficiary_id`) REFERENCES `beneficiaries` (`id`),
  CONSTRAINT `FK978bgsminmpxcogwo3m10qk2g` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`),
  CONSTRAINT `FKl2ekkqisi0d84xky5y9mkonun` FOREIGN KEY (`source_account_id`) REFERENCES `accounts` (`id`),
  CONSTRAINT `FKlms9dy5pt8av8dtj6v5k0d5vk` FOREIGN KEY (`destination_account_id`) REFERENCES `accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `name` varchar(40) NOT NULL,
  `description` varchar(160) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKofx66keruapi6vyqpv6f2or37` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `savings_goals`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `savings_goals` (
  `saved_amount` decimal(19,2) NOT NULL,
  `target_amount` decimal(19,2) NOT NULL,
  `target_date` date NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `customer_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `icon` varchar(40) DEFAULT NULL,
  `name` varchar(120) NOT NULL,
  `status` enum('ABANDONED','ACHIEVED','ACTIVE') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_goal_customer` (`customer_id`,`status`),
  CONSTRAINT `FKqth7hir84pm3dg77w73427kbx` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `transactions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `transactions` (
  `amount` decimal(19,2) NOT NULL,
  `balance_after` decimal(19,2) DEFAULT NULL,
  `category_user_set` bit(1) NOT NULL,
  `risk_score` int DEFAULT NULL,
  `account_id` bigint NOT NULL,
  `booked_at` datetime(6) NOT NULL,
  `counterparty_account_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `reference` varchar(32) NOT NULL,
  `transfer_group_ref` varchar(32) DEFAULT NULL,
  `counterparty_name` varchar(120) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `failure_reason` varchar(255) DEFAULT NULL,
  `category` enum('EDUCATION','ENTERTAINMENT','FOOD','HEALTHCARE','INCOME','OTHER','RENT','SAVINGS','SHOPPING','TRANSFER','TRANSPORT','UTILITIES') DEFAULT NULL,
  `direction` enum('CREDIT','DEBIT') NOT NULL,
  `risk_level` enum('CRITICAL','HIGH','LOW','MEDIUM') DEFAULT NULL,
  `status` enum('AWAITING_VERIFICATION','BLOCKED','COMPLETED','FAILED','PENDING','REVERSED') NOT NULL,
  `transaction_type` enum('BILL_PAYMENT','DEPOSIT','FEE','INTEREST','INTERNAL_TRANSFER','REVERSAL','WITHDRAWAL') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_txn_reference` (`reference`),
  KEY `idx_txn_account_date` (`account_id`,`booked_at`),
  KEY `idx_txn_group` (`transfer_group_ref`),
  KEY `idx_txn_category` (`category`),
  KEY `FK7q5tmp2kmixl3wj5mosviv5lv` (`counterparty_account_id`),
  CONSTRAINT `FK20w7wsg13u9srbq3bd7chfxdh` FOREIGN KEY (`account_id`) REFERENCES `accounts` (`id`),
  CONSTRAINT `FK7q5tmp2kmixl3wj5mosviv5lv` FOREIGN KEY (`counterparty_account_id`) REFERENCES `accounts` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_roles` (
  `role_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`role_id`,`user_id`),
  KEY `FKhfh9dx7w3ubf1co1vdev94g3f` (`user_id`),
  CONSTRAINT `FKh8ciramu9cc9q3qcqiv4ue8a6` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`),
  CONSTRAINT `FKhfh9dx7w3ubf1co1vdev94g3f` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `enabled` bit(1) NOT NULL,
  `failed_login_attempts` int NOT NULL,
  `two_factor_enabled` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `customer_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_login_at` datetime(6) DEFAULT NULL,
  `locked_until` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `last_login_ip` varchar(45) DEFAULT NULL,
  `password_hash` varchar(100) NOT NULL,
  `username` varchar(160) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_user_username` (`username`),
  UNIQUE KEY `UK72efjd5u3nqtjdduip5wwvd39` (`customer_id`),
  CONSTRAINT `FKchxdoybbydcaj5smgxe0qq5mk` FOREIGN KEY (`customer_id`) REFERENCES `customers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


SET FOREIGN_KEY_CHECKS = 1;
