package org.motechproject.nms.region.domain;

import org.codehaus.jackson.annotate.JsonManagedReference;
import org.motechproject.mds.annotations.Cascade;
import org.motechproject.mds.annotations.Entity;
import org.motechproject.mds.annotations.Field;
import org.motechproject.mds.annotations.Ignore;
import org.motechproject.mds.annotations.InstanceLifecycleListeners;
import org.motechproject.mds.domain.MdsEntity;
import org.motechproject.nms.tracking.annotation.TrackClass;
import org.motechproject.nms.tracking.annotation.TrackFields;

import javax.jdo.annotations.Column;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.Unique;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class Models data for State location records
 */
@Entity(tableName = "nms_states")
@TrackClass
@TrackFields
@InstanceLifecycleListeners
public class State extends MdsEntity {

    @Field
    @Column(allowsNull = "false")
    @NotNull
    private String name;

    @Field
    @Unique
    @Column(allowsNull = "false")
    @NotNull
    private Long code;

    @Field
    @Cascade(delete = true)
    @Persistent(mappedBy = "state", defaultFetchGroup = "true")
    @JsonManagedReference
    private List<District> districts;

    public State() {
        this.districts = new ArrayList<>();
    }

    public State(String name, Long code) {
        this.name = name;
        this.code = code;
        this.districts = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getCode() {
        return code;
    }

    public void setCode(Long code) {
        this.code = code;
    }

    public List<District> getDistricts() {
        return districts;
    }

    public void setDistricts(List<District> districts) {
        this.districts = districts;
    }

    @Ignore
    public Set<Circle> getCircles() {
        Set<Circle> circles = new HashSet<>();

        for (District district : this.getDistricts()) {
            circles.add(district.getCircle());
        }

        return circles;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        State state = (State) o;

        if (name != null ? !name.equals(state.name) : state.name != null) {
            return false;
        }
        return !(code != null ? !code.equals(state.code) : state.code != null);

    }

    @Override
    public int hashCode() {
        int result = name != null ? name.hashCode() : 0;
        result = 31 * result + (code != null ? code.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "State{" +
                "name='" + name + '\'' +
                ", code=" + code +
                '}';
    }
}
