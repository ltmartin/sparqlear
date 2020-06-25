package base.services;

import base.domain.Property;
import base.repository.PropertyRepository;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class PropertiesService {
    private final Logger logger = Logger.getLogger(PropertiesService.class.getName());
    @Resource
    private PropertyRepository propertyRepository;

    public Hashtable<Integer, Property> loadProperties() {
        logger.log(Level.INFO, "Loading ranked properties...");
        Hashtable<Integer, Property> rankedProperties = new Hashtable<>();
        propertyRepository.findAll().forEach(property -> rankedProperties.put(property.hashCode(), property));
        logger.log(Level.INFO, "Ranked properties successfully loaded.");
        return rankedProperties;
    }
}
