package com.laminar.workspace;

import com.laminar.context.WorkspaceContext;
import com.laminar.context.WorkspaceContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 워크스페이스 도메인 서비스.
 *
 * 가입 직후 personal workspace 자동 생성 + owner 멤버십 INSERT — 단일 트랜잭션.
 * slug 충돌 시 -2, -3 등 suffix 자동 부여 (최대 5회 retry, 이후 IllegalStateException).
 */
@Service
public class WorkspaceService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_SLUG_RETRY = 5;

    private final WorkspaceRepository workspaceRepo;
    private final WorkspaceMemberRepository memberRepo;

    public WorkspaceService(WorkspaceRepository workspaceRepo, WorkspaceMemberRepository memberRepo) {
        this.workspaceRepo = workspaceRepo;
        this.memberRepo = memberRepo;
    }

    /**
     * 가입 직후 호출 — displayName 기반 slug + owner 멤버십까지 한 번에.
     */
    @Transactional
    public WorkspaceEntity createPersonalWorkspace(UUID userId, String displayName) {
        String baseSlug = slugify(displayName);
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setName(displayName + "의 워크스페이스");
        workspace.setSlug(resolveUniqueSlug(baseSlug));
        workspace.setOwnerUserId(userId);
        workspace.setDefaultTimezone("Asia/Seoul");
        workspace.setSettings(new HashMap<>());
        WorkspaceEntity saved = workspaceRepo.save(workspace);

        WorkspaceMemberEntity owner = new WorkspaceMemberEntity();
        owner.setId(new WorkspaceMemberId(saved.getId(), userId));
        owner.setRole(WorkspaceRole.OWNER);
        memberRepo.save(owner);

        return saved;
    }

    @Transactional
    public WorkspaceEntity create(UUID ownerUserId, String name, String slug, String timezone) {
        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setName(name);
        workspace.setSlug(resolveUniqueSlug(slug));
        workspace.setOwnerUserId(ownerUserId);
        workspace.setDefaultTimezone(timezone == null ? "Asia/Seoul" : timezone);
        workspace.setSettings(new HashMap<>());
        WorkspaceEntity saved = workspaceRepo.save(workspace);

        WorkspaceMemberEntity owner = new WorkspaceMemberEntity();
        owner.setId(new WorkspaceMemberId(saved.getId(), ownerUserId));
        owner.setRole(WorkspaceRole.OWNER);
        memberRepo.save(owner);

        return saved;
    }

    @Transactional(readOnly = true)
    public WorkspaceEntity requireCurrent() {
        WorkspaceContext context = WorkspaceContextHolder.require();
        if (context.scope() == WorkspaceContext.Scope.SYSTEM) {
            throw new IllegalStateException("workspace not selected");
        }
        return workspaceRepo.findById(context.workspaceId())
                .orElseThrow(() -> new IllegalStateException("workspace not found in context"));
    }

    @Transactional(readOnly = true)
    public List<WorkspaceMemberEntity> listActiveMembershipsForUser(UUID userId) {
        return memberRepo.findByIdWorkspaceIdAndRemovedAtIsNull(
                WorkspaceContextHolder.require().workspaceId());
    }

    /**
     * 인증 사용자가 속한 모든 워크스페이스 — 가입 직후 SYSTEM scope에서 워크스페이스 발견용.
     * 워크스페이스 헤더 없이 호출되며 principal.userId 기준으로만 조회(타인 노출 없음).
     */
    @Transactional(readOnly = true)
    public List<WorkspaceEntity> listForUser(UUID userId) {
        List<UUID> workspaceIds = memberRepo.findAllByIdUserIdAndRemovedAtIsNull(userId).stream()
                .map(m -> m.getId().getWorkspaceId())
                .toList();
        return workspaceRepo.findAllById(workspaceIds);
    }

    @Transactional
    public WorkspaceEntity updateCurrent(String name, String timezone, Map<String, Object> settings) {
        WorkspaceEntity workspace = requireCurrent();
        if (name != null && !name.isBlank()) {
            workspace.setName(name);
        }
        if (timezone != null && !timezone.isBlank()) {
            workspace.setDefaultTimezone(timezone);
        }
        if (settings != null) {
            workspace.setSettings(settings);
        }
        return workspaceRepo.save(workspace);
    }

    private String resolveUniqueSlug(String baseSlug) {
        String candidate = baseSlug;
        for (int attempt = 1; attempt <= MAX_SLUG_RETRY; attempt++) {
            if (workspaceRepo.findBySlug(candidate).isEmpty()) {
                return candidate;
            }
            candidate = baseSlug + "-" + (RANDOM.nextInt(900) + 100);
        }
        throw new IllegalStateException("slug collision exhausted retries: " + baseSlug);
    }

    static String slugify(String input) {
        if (input == null || input.isBlank()) {
            return "ws-" + (RANDOM.nextInt(9000) + 1000);
        }
        String lower = input.trim().toLowerCase();
        String ascii = lower.replaceAll("[^a-z0-9가-힣\\-]+", "-");
        String trimmed = ascii.replaceAll("^-+|-+$", "");
        if (trimmed.length() > 40) {
            trimmed = trimmed.substring(0, 40);
        }
        return trimmed.isEmpty() ? "ws-" + (RANDOM.nextInt(9000) + 1000) : trimmed;
    }
}
