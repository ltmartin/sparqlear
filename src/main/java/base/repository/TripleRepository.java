package base.repository;

import base.domain.Triple;
import org.springframework.data.repository.CrudRepository;

public interface TripleRepository extends CrudRepository<Triple, Long> {
}
