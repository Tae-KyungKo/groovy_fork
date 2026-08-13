package com.groovy.backend.domain.memoir.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.groovy.backend.domain.memoir.Memoir;
import com.groovy.backend.domain.memoir.MemoirComment;
import com.groovy.backend.domain.memoir.dto.MemoirCommentRequest;
import com.groovy.backend.domain.memoir.dto.MemoirCommentResponse;
import com.groovy.backend.domain.memoir.repository.MemoirCommentRepository;
import com.groovy.backend.domain.user.User;
import com.groovy.backend.domain.user.repository.UserRepository;
import com.groovy.backend.global.exception.ForbiddenException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemoirCommentService {

	// 댓글 작성 시 회고록이 연결된 스터디가 얻는 경험치. 레벨업 기준은 Study.addExp를 참고.
	private static final int COMMENT_EXP = 5;

	private final MemoirCommentRepository memoirCommentRepository;
	private final MemoirService memoirService;
	private final UserRepository userRepository;

	@Transactional
	public MemoirCommentResponse createComment(String email, Long memoirId, MemoirCommentRequest request) {
		User author = getUser(email);
		Memoir memoir = memoirService.getMemoirEntity(memoirId);

		MemoirComment comment = MemoirComment.builder()
			.memoir(memoir)
			.author(author)
			.content(request.content())
			.build();

		MemoirComment saved = memoirCommentRepository.save(comment);
		memoir.getStudy().addExp(COMMENT_EXP);
		log.info("회고록 댓글 작성: memoirId={}, commentId={}, authorId={}", memoirId, saved.getId(), author.getId());

		return MemoirCommentResponse.from(saved);
	}

	public List<MemoirCommentResponse> getComments(Long memoirId) {
		return memoirCommentRepository.findByMemoirIdWithAuthor(memoirId).stream()
			.map(MemoirCommentResponse::from)
			.toList();
	}

	@Transactional
	public MemoirCommentResponse updateComment(String email, Long memoirId, Long commentId, MemoirCommentRequest request) {
		MemoirComment comment = getCommentEntity(memoirId, commentId);
		validateAuthor(comment, email);

		comment.update(request.content());
		log.info("회고록 댓글 수정: memoirId={}, commentId={}, email={}", memoirId, commentId, email);

		return MemoirCommentResponse.from(comment);
	}

	@Transactional
	public void deleteComment(String email, Long memoirId, Long commentId) {
		MemoirComment comment = getCommentEntity(memoirId, commentId);
		validateAuthor(comment, email);

		memoirCommentRepository.delete(comment);
		log.info("회고록 댓글 삭제: memoirId={}, commentId={}, email={}", memoirId, commentId, email);
	}

	private MemoirComment getCommentEntity(Long memoirId, Long commentId) {
		return memoirCommentRepository.findById(commentId)
			.filter(comment -> comment.getMemoir().getId().equals(memoirId))
			.orElseThrow(() -> {
				log.warn("존재하지 않는 댓글: memoirId={}, commentId={}", memoirId, commentId);
				return new IllegalArgumentException("존재하지 않는 댓글입니다.");
			});
	}

	private void validateAuthor(MemoirComment comment, String email) {
		User user = getUser(email);
		if (!comment.isAuthor(user.getId())) {
			log.warn("댓글 작성자 아님: commentId={}, email={}", comment.getId(), email);
			throw new ForbiddenException("작성자만 수행할 수 있는 작업입니다.");
		}
	}

	private User getUser(String email) {
		return userRepository.findByEmail(email)
			.orElseThrow(() -> {
				log.warn("존재하지 않는 유저: email={}", email);
				return new IllegalArgumentException("존재하지 않는 유저입니다.");
			});
	}
}
