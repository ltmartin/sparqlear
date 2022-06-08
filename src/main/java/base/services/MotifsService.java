package base.services;

import base.domain.Motif;
import base.domain.Triple;
import base.repository.MotifRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

@Service
public class MotifsService {
    private final Logger logger = Logger.getLogger(MotifsService.class.getName());
    @Resource
    private MotifRepository motifRepository;

    private List<Motif> candidateMotifInstances = new LinkedList<>();

    public Set<Motif> findMotifsInvolvingIndividual(String individual){

        Set<Motif> candidateMotifInstances = new HashSet<>();

        candidateMotifInstances.addAll(motifRepository.findByTriples_subject(individual));
        candidateMotifInstances.addAll(motifRepository.findByTriples_object(individual));

        return candidateMotifInstances;
    }

    @Cacheable(cacheNames = "motifCache", value = "motifCache")
    @PostConstruct
    public List<Motif> loadAllMotifs(){
        if (candidateMotifInstances.isEmpty()) {
            Iterable<Motif> allMotifs = motifRepository.findAll();
            allMotifs.forEach(motif -> candidateMotifInstances.add(motif));
        }

        return candidateMotifInstances;
    }

}
