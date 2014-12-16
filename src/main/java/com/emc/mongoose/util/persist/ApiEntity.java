package com.emc.mongoose.util.persist;
//
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
//
import static javax.persistence.GenerationType.IDENTITY;
//
/**
 * Created by olga on 17.10.14.
 */
@Entity(name="API")
@Table(name = "API", uniqueConstraints = {
	@UniqueConstraint(columnNames = "name")})
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public final class ApiEntity
implements Serializable {
	@Id
	@GeneratedValue(strategy = IDENTITY)
	@Column(name = "id")
	private long id;
	@Column(name = "name")
	private String name;
	@OneToMany(targetEntity=LoadEntity.class, fetch = FetchType.LAZY, mappedBy = "api")
	private Set<LoadEntity> loadsSet = new HashSet<LoadEntity>();
	//
	public ApiEntity(){
	}
	public ApiEntity(final String name){
		this.name = name;
	}
	//
	public final long getId() {
		return id;
	}
	public final void setId(final long id) {
		this.id = id;
	}
	public final String getName() {
		return name;
	}
	public final void setName(final String name) {
		this.name = name;
	}
	public final Set<LoadEntity> getLoadsSet() {
		return loadsSet;
	}
	public final void setLoadsSet(final Set<LoadEntity> loadsSet) {
		this.loadsSet = loadsSet;
	}
}
