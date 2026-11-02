-- OBMS - reference data
--
-- GENERATED FILE. Do not edit by hand.
--
-- The roles and biller catalogue that ReferenceDataSeeder
-- inserts at startup. Apply after schema.sql if you want the
-- reference rows without running the application.
--
-- Demo customers and their transaction history are NOT here:
-- they are generated at runtime with randomised amounts by
-- DemoDataSeeder, enabled with OBMS_DEMO=true.


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

LOCK TABLES `roles` WRITE;
/*!40000 ALTER TABLE `roles` DISABLE KEYS */;
INSERT INTO `roles` (`created_at`, `id`, `updated_at`, `name`, `description`) VALUES ('2026-11-02 16:29:04.188500',1,'2026-11-02 16:29:04.188500','CUSTOMER','Retail banking customer'),('2026-11-02 16:29:04.269782',2,'2026-11-02 16:29:04.269782','BANK_STAFF','Branch staff: onboarding and KYC review'),('2026-11-02 16:29:04.275710',3,'2026-11-02 16:29:04.275710','ADMIN','System administrator: configuration and audit'),('2026-11-02 16:29:04.280014',4,'2026-11-02 16:29:04.280014','FRAUD_ANALYST','Reviews and dispositions fraud alerts');
/*!40000 ALTER TABLE `roles` ENABLE KEYS */;
UNLOCK TABLES;

LOCK TABLES `billers` WRITE;
/*!40000 ALTER TABLE `billers` DISABLE KEYS */;
INSERT INTO `billers` (`active`, `max_amount`, `min_amount`, `created_at`, `id`, `updated_at`, `code`, `identifier_label`, `name`, `category`, `spend_category`) VALUES (_binary '',50000.00,50.00,'2026-11-02 16:29:04.289853',1,'2026-11-02 16:29:04.289853','BESCOM','Consumer Number','BESCOM Electricity','ELECTRICITY','UTILITIES'),(_binary '',50000.00,50.00,'2026-11-02 16:29:04.304911',2,'2026-11-02 16:29:04.304911','MSEDCL','Consumer Number','MSEDCL Power','ELECTRICITY','UTILITIES'),(_binary '',20000.00,50.00,'2026-11-02 16:29:04.310491',3,'2026-11-02 16:29:04.310491','BWSSB','RR Number','BWSSB Water Supply','WATER','UTILITIES'),(_binary '',5000.00,10.00,'2026-11-02 16:29:04.316393',4,'2026-11-02 16:29:04.316393','AIRTEL-MOB','Mobile Number','Airtel Mobile','MOBILE_RECHARGE','UTILITIES'),(_binary '',5000.00,10.00,'2026-11-02 16:29:04.322017',5,'2026-11-02 16:29:04.322017','JIO-MOB','Mobile Number','Jio Mobile','MOBILE_RECHARGE','UTILITIES'),(_binary '',30000.00,100.00,'2026-11-02 16:29:04.327248',6,'2026-11-02 16:29:04.327248','ACT-BB','Account Number','ACT Fibernet Broadband','BROADBAND','UTILITIES'),(_binary '',30000.00,100.00,'2026-11-02 16:29:04.332391',7,'2026-11-02 16:29:04.332391','AIRTEL-BB','Landline Number','Airtel Xstream Broadband','BROADBAND','UTILITIES'),(_binary '',10000.00,100.00,'2026-11-02 16:29:04.336999',8,'2026-11-02 16:29:04.336999','IOC-GAS','Consumer Number','Indane Gas','GAS','UTILITIES'),(_binary '',10000.00,100.00,'2026-11-02 16:29:04.341210',9,'2026-11-02 16:29:04.341210','GAIL-GAS','Consumer Number','GAIL Piped Gas','GAS','UTILITIES'),(_binary '',200000.00,100.00,'2026-11-02 16:29:04.345839',10,'2026-11-02 16:29:04.345839','OBMS-CC','Card Last 4 Digits','OBMS Credit Card','CREDIT_CARD','UTILITIES');
/*!40000 ALTER TABLE `billers` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

