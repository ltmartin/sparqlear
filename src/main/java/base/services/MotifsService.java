package base.services;

import base.domain.Motif;
import base.domain.Triple;
import base.repository.MotifRepository;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

@Service
public class MotifsService {
    private final Logger logger = Logger.getLogger(PropertiesService.class.getName());
    @Resource
    private MotifRepository motifRepository;

    public Set<Motif> findMotifsInvolvingIndividual(String individual){

        Set<Motif> candidateMotifInstances = new HashSet<>();

        candidateMotifInstances.addAll(motifRepository.findByTriples_subject(individual));
        candidateMotifInstances.addAll(motifRepository.findByTriples_object(individual));

        return candidateMotifInstances;
    }
}
