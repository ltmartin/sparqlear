package base.repository;

import base.domain.Property;
import org.springframework.data.repository.CrudRepository;


public interface PropertyRepository extends CrudRepository<Property, Long> {
    public Property findPropertyByLabel(String label);
}
