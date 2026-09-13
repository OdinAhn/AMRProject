package com.amrproject.repository.user;

import com.amrproject.domain.entity.user.User;
import com.amrproject.domain.entity.user.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /**
     * 26단계: TokenAuthenticationFilter는 트랜잭션 밖(서블릿 필터)에서 실행돼 LAZY인
     * role에 접근할 수 없으므로 role을 함께 가져온다.
     */
    @Query("select u from User u join fetch u.role where u.id = :id")
    Optional<User> findWithRoleById(Long id);

    boolean existsByRoleCode(String roleCode);

    /** 32단계: 마지막 활성 관리자 보호 — 역할·상태를 바꾸기 전에 남는 활성 관리자 수를 센다. */
    long countByRoleCodeAndStatus(String roleCode, UserStatus status);
}
