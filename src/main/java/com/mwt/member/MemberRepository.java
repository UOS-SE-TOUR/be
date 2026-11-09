package com.mwt.member;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 회원을 저장하고 찾는다(설계산출물 4.2). 다른 모듈은 이 인터페이스 대신 MemberService를 쓴다.
 * 아이디 비교와 유일 키는 MySQL 기본 정렬 규칙(utf8mb4_0900_ai_ci)을 따른다. 대소문자·악센트·전각과 반각을 가리지 않아
 * hong이 있으면 HONG·hóng·ｈｏｎｇ으로는 가입할 수 없고, 그 꼴로 로그인하면 hong으로 들어간다.
 */
interface MemberRepository extends JpaRepository<Member, Long> {

	boolean existsByLoginId(String loginId);

	Optional<Member> findByLoginId(String loginId);

}
