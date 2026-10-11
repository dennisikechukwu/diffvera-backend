package me.dennislab.diffvera.rule;

import me.dennislab.diffvera.repository.GitRepoRepository;
import me.dennislab.diffvera.repository.Repository;
import me.dennislab.diffvera.rule.dto.CreateRuleRequestDto;
import me.dennislab.diffvera.rule.dto.RuleResponseDto;
import me.dennislab.diffvera.rule.dto.RuleVersionDto;
import me.dennislab.diffvera.rule.dto.UpdateRuleRequestDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArchitectureRuleServiceTest {

    @Mock
    private ArchitectureRuleRepository ruleRepository;

    @Mock
    private RuleVersionRepository ruleVersionRepository;

    @Mock
    private GitRepoRepository gitRepoRepository;

    private ArchitectureRuleService ruleService;

    @BeforeEach
    void setUp() {
        ruleService = new ArchitectureRuleService(ruleRepository, ruleVersionRepository, gitRepoRepository);
    }

    @Test
    @DisplayName("createRule should create rule and initial Version 1 snapshot")
    void shouldCreateRuleAndInitialVersion() {
        UUID repoId = UUID.randomUUID();
        Repository repo = Repository.builder().id(repoId).name("order-service").fullName("acme/order-service").build();
        when(gitRepoRepository.findById(repoId)).thenReturn(Optional.of(repo));

        ArchitectureRule savedRule = ArchitectureRule.builder()
            .id(UUID.randomUUID())
            .repository(repo)
            .name("No direct DB in controllers")
            .isEnabled(true)
            .currentVersion(1)
            .build();
        when(ruleRepository.save(any(ArchitectureRule.class))).thenReturn(savedRule);

        CreateRuleRequestDto request = CreateRuleRequestDto.builder()
            .name("No direct DB in controllers")
            .boundary("Controllers must not import repositories or call database directly.")
            .build();

        RuleResponseDto response = ruleService.createRule(repoId, request);

        assertThat(response.getId()).isEqualTo(savedRule.getId());
        assertThat(response.getName()).isEqualTo("No direct DB in controllers");
        assertThat(response.getBoundary()).isEqualTo("Controllers must not import repositories or call database directly.");
        assertThat(response.getCurrentVersion()).isEqualTo(1);
        assertThat(response.getIsEnabled()).isTrue();

        ArgumentCaptor<RuleVersion> versionCaptor = ArgumentCaptor.forClass(RuleVersion.class);
        verify(ruleVersionRepository).save(versionCaptor.capture());
        RuleVersion savedVersion = versionCaptor.getValue();
        assertThat(savedVersion.getVersionNumber()).isEqualTo(1);
        assertThat(savedVersion.getBoundary()).isEqualTo("Controllers must not import repositories or call database directly.");
    }

    @Test
    @DisplayName("updateRule with modified boundary should increment version and record new RuleVersion")
    void shouldCreateNewVersionWhenBoundaryChanges() {
        UUID ruleId = UUID.randomUUID();
        Repository repo = Repository.builder().id(UUID.randomUUID()).build();
        ArchitectureRule rule = ArchitectureRule.builder()
            .id(ruleId)
            .repository(repo)
            .name("Controller Boundary")
            .isEnabled(true)
            .currentVersion(1)
            .build();

        RuleVersion version1 = RuleVersion.builder()
            .id(UUID.randomUUID())
            .rule(rule)
            .versionNumber(1)
            .name("Controller Boundary")
            .boundary("Old constraint description")
            .build();

        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
        when(ruleVersionRepository.findByRuleIdAndVersionNumber(ruleId, 1)).thenReturn(Optional.of(version1));
        when(ruleRepository.save(any(ArchitectureRule.class))).thenAnswer(i -> i.getArgument(0));

        UpdateRuleRequestDto request = UpdateRuleRequestDto.builder()
            .name("Controller Boundary")
            .boundary("Updated new constraint description")
            .build();

        RuleResponseDto updated = ruleService.updateRule(ruleId, request);

        assertThat(updated.getCurrentVersion()).isEqualTo(2);
        assertThat(updated.getBoundary()).isEqualTo("Updated new constraint description");

        ArgumentCaptor<RuleVersion> versionCaptor = ArgumentCaptor.forClass(RuleVersion.class);
        verify(ruleVersionRepository).save(versionCaptor.capture());
        RuleVersion savedVersion2 = versionCaptor.getValue();
        assertThat(savedVersion2.getVersionNumber()).isEqualTo(2);
        assertThat(savedVersion2.getBoundary()).isEqualTo("Updated new constraint description");
    }

    @Test
    @DisplayName("toggleRule should enable or disable rule without changing version")
    void shouldToggleRule() {
        UUID ruleId = UUID.randomUUID();
        Repository repo = Repository.builder().id(UUID.randomUUID()).build();
        ArchitectureRule rule = ArchitectureRule.builder()
            .id(ruleId)
            .repository(repo)
            .name("Some Rule")
            .isEnabled(true)
            .currentVersion(1)
            .build();

        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
        when(ruleRepository.save(any(ArchitectureRule.class))).thenAnswer(i -> i.getArgument(0));

        RuleResponseDto toggled = ruleService.toggleRule(ruleId, false);

        assertThat(toggled.getIsEnabled()).isFalse();
        assertThat(toggled.getCurrentVersion()).isEqualTo(1);
    }

    @Test
    @DisplayName("getRuleVersionHistory should return all versions ordered by version number desc")
    void shouldReturnRuleVersionHistory() {
        UUID ruleId = UUID.randomUUID();
        Repository repo = Repository.builder().id(UUID.randomUUID()).build();
        ArchitectureRule rule = ArchitectureRule.builder()
            .id(ruleId)
            .repository(repo)
            .name("Rule")
            .isEnabled(true)
            .currentVersion(2)
            .build();

        RuleVersion v2 = RuleVersion.builder().id(UUID.randomUUID()).versionNumber(2).name("Rule").boundary("V2").build();
        RuleVersion v1 = RuleVersion.builder().id(UUID.randomUUID()).versionNumber(1).name("Rule").boundary("V1").build();

        when(ruleRepository.findById(ruleId)).thenReturn(Optional.of(rule));
        when(ruleVersionRepository.findByRuleIdOrderByVersionNumberDesc(ruleId)).thenReturn(List.of(v2, v1));

        List<RuleVersionDto> history = ruleService.getRuleVersionHistory(ruleId);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getVersionNumber()).isEqualTo(2);
        assertThat(history.get(1).getVersionNumber()).isEqualTo(1);
    }
}
