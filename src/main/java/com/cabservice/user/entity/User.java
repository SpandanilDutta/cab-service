package com.cabservice.user.entity;

import com.cabservice.common.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;

@Entity
@Table(name = "users")
public class User extends BaseEntity implements Serializable {
}
