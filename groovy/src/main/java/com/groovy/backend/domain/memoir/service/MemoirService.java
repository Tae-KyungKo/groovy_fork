package com.groovy.backend.domain.memoir.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.groovy.backend.domain.memoir.Memoir;
import com.groovy.backend.domain.memoir.MemoirLike;
import com.groovy.backend.domain.memoir.dto.MemoirCreateRequest;
import com.groovy.backend.domain.memoir.dto.MemoirResponse;
import com.groovy.backend.domain.memoir.dto.MemoirStudyOptionResponse;
import com.groovy.backend.domain.memoir.dto.MemoirUpdateRequest;
import com.groovy.backend.domain.memoir.repository.MemoirCommentRepository;
import com.groovy.backend.domain.memoir.repository.MemoirCommentRepository.MemoirCommentCount;
import com.groovy.backend.domain.memoir.repository.MemoirLikeRepository;
import com.groovy.backend.domain.memoir.repository.MemoirLikeRepository.MemoirLikeCount;
import com.groovy.backend.domain.memoir.repository.MemoirRepository;
import com.groovy.backend.domain.study.Application;
import com.groovy.backend.domain.study.ApplicationStatus;
import com.groovy.backend.domain.study.Study;
import com.groovy.backend.domain.study.repository.ApplicationRepository;
import com.groovy.backend.domain.study.repository.StudyRepository;
import com.groovy.backend.domain.user.User;
import com.groovy.backend.domain.user.repository.UserRepository;
import com.groovy.backend.global.exception.ForbiddenException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemoirService {

	// 회고록/댓글 작성 시 연결된 스터디가 얻는 경험치. 레벨업 기준은 Study.addExp를 참고.
	private static final int MEMOIR_EXP = 20;
	private static final String SORT_POPULAR = "popular";

	private final MemoirRepository memoirRepository;
	private final MemoirCommentRepository memoirCommentRepository;
	private final MemoirLikeRepository memoirLikeRepository;
	private final StudyRepository studyRepository;
	private final ApplicationRepository applicationRepository;
	private final UserRepository userRepository;

	@Transactional
	public MemoirResponse createMemoir(String email, MemoirCreateRequest request) {
		User author = getUser(email);
		Study study = resolveMyStudy(author, request.studyId());

		Memoir memoir = Memoir.builder()
			.study(study)
			.author(author)
			.title(request.title())
			.content(request.content())
			.build();

		Memoir saved = memoirRepository.save(memoir);
		study.addExp(MEMOIR_EXP);
		log.info("회고록 작성: memoirId={}, studyId={}, authorId={}", saved.getId(), study.getId(), author.getId());

		return MemoirResponse.from(saved, 0L, 0L, false);
	}

	// keyword가 비어 있으면 전체 목록, sort="popular"면 좋아요+댓글 수 기준 인기순, 그 외엔 최신순.
	public Page<MemoirResponse> getMemoirs(String keyword, String sortBy, String viewerEmail, Pageable pageable) {
		String normalizedKeyword = normalizeKeyword(keyword);
		Page<Memoir> memoirs = SORT_POPULAR.equals(sortBy)
			? getMemoirsByPopularity(normalizedKeyword, pageable)
			: memoirRepository.search(normalizedKeyword, pageable);

		return toResponsePage(memoirs, viewerEmail);
	}

	public Page<MemoirResponse> getMyMemoirs(String email, Pageable pageable) {
		User user = getUser(email);
		Page<Memoir> memoirs = memoirRepository.findByAuthorIdWithStudyAndAuthor(user.getId(), pageable);
		return toResponsePage(memoirs, email);
	}

	public MemoirResponse getMemoir(Long memoirId, String viewerEmail) {
		Memoir memoir = getMemoirEntity(memoirId);
		long commentCount = memoirCommentRepository.countByMemoirId(memoirId);
		long likeCount = memoirLikeRepository.countByMemoirId(memoirId);
		boolean liked = isLikedByViewer(memoirId, viewerEmail);

		return MemoirResponse.from(memoir, commentCount, likeCount, liked);
	}

	@Transactional
	public MemoirResponse updateMemoir(String email, Long memoirId, MemoirUpdateRequest request) {
		Memoir memoir = getMemoirEntity(memoirId);
		validateAuthor(memoir, email);

		memoir.update(request.title(), request.content());
		log.info("회고록 수정: memoirId={}, email={}", memoirId, email);

		long commentCount = memoirCommentRepository.countByMemoirId(memoirId);
		long likeCount = memoirLikeRepository.countByMemoirId(memoirId);
		boolean liked = isLikedByViewer(memoirId, email);
		return MemoirResponse.from(memoir, commentCount, likeCount, liked);
	}

	@Transactional
	public void deleteMemoir(String email, Long memoirId) {
		Memoir memoir = getMemoirEntity(memoirId);
		validateAuthor(memoir, email);

		memoirCommentRepository.deleteAllByMemoirId(memoirId);
		memoirLikeRepository.deleteAllByMemoirId(memoirId);
		memoirRepository.delete(memoir);
		log.info("회고록 삭제: memoirId={}, email={}", memoirId, email);
	}

	@Transactional
	public MemoirResponse likeMemoir(String email, Long memoirId) {
		User user = getUser(email);
		Memoir memoir = getMemoirEntity(memoirId);

		if (!memoirLikeRepository.existsByMemoirIdAndUserId(memoirId, user.getId())) {
			memoirLikeRepository.save(MemoirLike.builder().memoir(memoir).user(user).build());
			log.info("회고록 좋아요: memoirId={}, userId={}", memoirId, user.getId());
		}

		long commentCount = memoirCommentRepository.countByMemoirId(memoirId);
		long likeCount = memoirLikeRepository.countByMemoirId(memoirId);
		return MemoirResponse.from(memoir, commentCount, likeCount, true);
	}

	@Transactional
	public MemoirResponse unlikeMemoir(String email, Long memoirId) {
		User user = getUser(email);
		Memoir memoir = getMemoirEntity(memoirId);

		memoirLikeRepository.findByMemoirIdAndUserId(memoirId, user.getId())
			.ifPresent(memoirLikeRepository::delete);
		log.info("회고록 좋아요 취소: memoirId={}, userId={}", memoirId, user.getId());

		long commentCount = memoirCommentRepository.countByMemoirId(memoirId);
		long likeCount = memoirLikeRepository.countByMemoirId(memoirId);
		return MemoirResponse.from(memoir, commentCount, likeCount, false);
	}

	// 회고록 작성 시 고를 수 있는, 내가 방장이거나 승인되어 속한 스터디 목록.
	public List<MemoirStudyOptionResponse> getMyStudyOptions(String email) {
		User user = getUser(email);
		return getMyStudies(user).stream()
			.map(MemoirStudyOptionResponse::from)
			.toList();
	}

	Memoir getMemoirEntity(Long memoirId) {
		return memoirRepository.findByIdWithStudyAndAuthor(memoirId)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 회고록: memoirId={}", memoirId);
				return new IllegalArgumentException("존재하지 않는 회고록입니다.");
			});
	}

	void validateAuthor(Memoir memoir, String email) {
		User user = getUser(email);
		if (!memoir.isAuthor(user.getId())) {
			log.warn("회고록 작성자 아님: memoirId={}, email={}", memoir.getId(), email);
			throw new ForbiddenException("작성자만 수행할 수 있는 작업입니다.");
		}
	}

	// 네이티브 쿼리 자체에 이미 인기순 ORDER BY가 있어서, Pageable에 정렬 정보(기본값 id desc)가
	// 실려 있으면 스프링 데이터가 그 뒤에 "order by id desc"를 또 덧붙여 SQL 문법 오류가 난다.
	// 정렬을 뺀 페이지 범위(페이지 번호/크기)만 남긴 Pageable로 바꿔서 넘긴다.
	private Page<Memoir> getMemoirsByPopularity(String keyword, Pageable pageable) {
		Pageable pageRange = Pageable.ofSize(pageable.getPageSize()).withPage(pageable.getPageNumber());
		Page<Long> idPage = memoirRepository.findIdsByPopularity(keyword, pageRange);
		List<Long> ids = idPage.getContent();
		if (ids.isEmpty()) {
			return new PageImpl<>(List.of(), pageRange, idPage.getTotalElements());
		}

		Map<Long, Memoir> memoirById = memoirRepository.findAllWithStudyAndAuthorByIdIn(ids).stream()
			.collect(Collectors.toMap(Memoir::getId, memoir -> memoir));
		List<Memoir> ordered = ids.stream().map(memoirById::get).toList();

		return new PageImpl<>(ordered, pageRange, idPage.getTotalElements());
	}

	private Page<MemoirResponse> toResponsePage(Page<Memoir> memoirs, String viewerEmail) {
		List<Long> memoirIds = memoirs.getContent().stream().map(Memoir::getId).toList();

		Map<Long, Long> commentCountByMemoirId = toCountMap(
			memoirCommentRepository.countByMemoirIdIn(memoirIds), MemoirCommentCount::getMemoirId, MemoirCommentCount::getCommentCount);
		Map<Long, Long> likeCountByMemoirId = toCountMap(
			memoirLikeRepository.countByMemoirIdIn(memoirIds), MemoirLikeCount::getMemoirId, MemoirLikeCount::getLikeCount);
		Set<Long> likedMemoirIds = resolveLikedMemoirIds(viewerEmail, memoirIds);

		return memoirs.map(memoir -> MemoirResponse.from(
			memoir,
			commentCountByMemoirId.getOrDefault(memoir.getId(), 0L),
			likeCountByMemoirId.getOrDefault(memoir.getId(), 0L),
			likedMemoirIds.contains(memoir.getId())
		));
	}

	private <T> Map<Long, Long> toCountMap(List<T> rows, Function<T, Long> idFn, ToLongFunction<T> countFn) {
		Map<Long, Long> result = new LinkedHashMap<>();
		rows.forEach(row -> result.put(idFn.apply(row), countFn.applyAsLong(row)));
		return result;
	}

	private Set<Long> resolveLikedMemoirIds(String viewerEmail, List<Long> memoirIds) {
		if (viewerEmail == null || memoirIds.isEmpty()) {
			return Set.of();
		}
		User viewer = getUser(viewerEmail);
		return Set.copyOf(memoirLikeRepository.findLikedMemoirIds(viewer.getId(), memoirIds));
	}

	private boolean isLikedByViewer(Long memoirId, String viewerEmail) {
		if (viewerEmail == null) {
			return false;
		}
		User viewer = getUser(viewerEmail);
		return memoirLikeRepository.existsByMemoirIdAndUserId(memoirId, viewer.getId());
	}

	private String normalizeKeyword(String keyword) {
		return StringUtils.hasText(keyword) ? keyword.trim() : null;
	}

	private Study resolveMyStudy(User author, Long studyId) {
		Study study = studyRepository.findById(studyId)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 스터디: studyId={}", studyId);
				return new IllegalArgumentException("존재하지 않는 스터디입니다.");
			});

		boolean isMember = study.isLeader(author.getId())
			|| applicationRepository.existsByStudyIdAndApplicantIdAndStatus(studyId, author.getId(), ApplicationStatus.APPROVED);
		if (!isMember) {
			log.warn("스터디 멤버 아님: studyId={}, userId={}", studyId, author.getId());
			throw new ForbiddenException("참여 중인 스터디만 회고록으로 연결할 수 있습니다.");
		}

		return study;
	}

	// 내가 방장인 스터디와, 참여 신청이 승인된 스터디를 id 기준 중복 없이 합쳐 반환한다.
	private List<Study> getMyStudies(User user) {
		Map<Long, Study> studiesById = new LinkedHashMap<>();

		studyRepository.findByLeaderId(user.getId())
			.forEach(study -> studiesById.put(study.getId(), study));

		applicationRepository.findByApplicantIdAndStatus(user.getId(), ApplicationStatus.APPROVED).stream()
			.map(Application::getStudy)
			.forEach(study -> studiesById.put(study.getId(), study));

		return List.copyOf(studiesById.values());
	}

	private User getUser(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 유저: email={}", email);
				return new IllegalArgumentException("존재하지 않는 유저입니다.");
			});
	}
}
