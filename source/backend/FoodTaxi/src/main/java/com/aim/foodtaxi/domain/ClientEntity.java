package com.aim.foodtaxi.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "T_CLIENT")
@Getter
@Setter
@ToString
public class ClientEntity {

	@Id
	@GeneratedValue(strategy=GenerationType.TABLE, generator="client_seq_gen")
    @SequenceGenerator(name="client_seq_gen", sequenceName = "client_seq", allocationSize = 1, initialValue=50)
    @Column
    private Long id;
	
	@Column(name="NAME")
	private String name;
	
	@Column(name="CODE")
	private String code;
	
	@Column(name = "COMPANY_NUMBER")
	private String companyNumber;
	
	@Column(name="EMAIl")
	private String email;
	
	@Column(name="ADDRESS")
	private String address;
	
	@Column(name="APP_KEY")
	private String appKey;
}
