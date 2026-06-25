package pk.ak.pasir_krawiec_antoni.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import pk.ak.pasir_krawiec_antoni.model.Transaction;
import pk.ak.pasir_krawiec_antoni.model.User;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findAllByUser(User user);
    List<Transaction> findByUser(User user);
    List<Transaction> findByUserAndTimestampGreaterThanEqual(User user, LocalDateTime timestamp);
}