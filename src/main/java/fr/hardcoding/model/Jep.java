package fr.hardcoding.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "jeps")
public class Jep extends PanacheEntity {
    public JepType type;
    public JepState state;
    public String release;
    public String component;
    public String subComponent;
    @Column(unique = true)
    public String number;
    public String title;

    public static Jep findByNumber(String number) {
        return find("number", number).firstResult();
    }

    @Override
    public String toString() {
        return "Jep{" +
                "type=" + this.type +
                ", state=" + this.state +
                ", release='" + this.release + '\'' +
                ", component='" + this.component + '\'' +
                ", subComponent='" + this.subComponent + '\'' +
                ", number='" + this.number + '\'' +
                ", title='" + this.title + '\'' +
                '}';
    }
}