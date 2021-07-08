package base.repository;

import base.domain.Motif;
import org.springframework.data.repository.CrudRepository;

import java.util.Set;

public interface MotifRepository extends CrudRepository<Motif, Long> {
    Set<Motif> findByTriples_subject(String subject);
    Set<Motif> findByTriples_object(String object);
}
