package base.domain;

import javax.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
public class Motif implements Cloneable{
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinTable(
            name = "motif_triples",
            joinColumns = @JoinColumn(name = "motif_id"),
            inverseJoinColumns = @JoinColumn(name = "triple_id")
    )
    private Set<Triple> triples;

    public Motif() {
        this.triples = new HashSet<>();
    }

    public Motif(Set<Triple> triples) {
        this.triples = triples;
    }

    public Boolean addTriple(Triple triple){
        return this.triples.add(triple);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Set<Triple> getTriples() {
        return triples;
    }

    public void setTriples(Set<Triple> triples) {
        this.triples = triples;
    }

    public Motif clone(){
        Motif newMotif = new Motif();
        for (Triple t : this.triples) {
            newMotif.addTriple(t.clone());
        }
        newMotif.setId(this.getId());
        return newMotif;
    }
}
