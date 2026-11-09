package com.mwt.member;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회원을 저장하고 찾는다(설계산출물 4.2). 다른 모듈은 이 인터페이스 대신 MemberService를 쓴다.
 * 아이디 비교는 DB 정렬 규칙(utf8mb4_0900_ai_ci)을 따르므로 대소문자를 가리지 않는다.
 */
interface MemberRepository extends JpaRepository<Member, Long> {

	boolean existsByLoginId(String loginId);

	Optional<Member> findByLoginId(String loginId);

}
