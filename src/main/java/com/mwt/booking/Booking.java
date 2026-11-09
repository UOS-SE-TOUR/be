package com.mwt.booking;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import com.mwt.member.Member;
import com.mwt.tour.Tour;
import com.mwt.tour.TourLevel;
import com.mwt.tour.TourOption;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 신청 엔티티다(설계산출물 4.5). totalPrice는 할인 전 총 가격이고, 최종 가격은 totalPrice − discount다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tour_id", nullable = false)
	private Tour tour;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TourLevel level;

	private int headcount;

	@ManyToMany
	@JoinTable(
			name = "booking_option",
			joinColumns = @JoinColumn(name = "booking_id"),
			inverseJoinColumns = @JoinColumn(name = "tour_option_id"))
	private Set<TourOption> options = new LinkedHashSet<>();

	private long totalPrice;

	private long discount;

	@Column(nullable = false)
	private Instant appliedAt;

}
