package com.mwt.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 엔티티다(설계산출물 4.2). password에는 암호화한 값만 담는다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String loginId;

	@Column(nullable = false)
	private String password;

	@Column(nullable = false, length = 50)
	private String name;

	@Column(nullable = false)
	private String address;

	@Column(nullable = false, length = 20)
	private String phone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private MemberType memberType;

}
