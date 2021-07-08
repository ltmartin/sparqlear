package base.services;

import base.Application;
import base.domain.Motif;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.Assert;

import javax.annotation.Resource;
import java.util.Set;

@SpringBootTest(classes = Application.class)
class MotifsServiceTest {
    @Resource
    private MotifsService motifsService;

    @Test
    public void findMotifsInvolvingIndividualTest(){
        String individual = "<http://dbpedia.org/resource/Alan_Kay>";
        Set<Motif> candidateMotifInstances = motifsService.findMotifsInvolvingIndividual(individual);

        Assert.notNull(candidateMotifInstances, "No instances were retrieved.");
    }
}