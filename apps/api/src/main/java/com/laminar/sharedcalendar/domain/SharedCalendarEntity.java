package com.laminar.sharedcalendar.domain;

import com.laminar.common.domain.SubjectScopedBaseEntity;
import com.laminar.tab.domain.TabDefaultView;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "shared_calendars")
@Filter(name = "subjectSharedFilter", condition = "subject_id = :ctxSubjectId")
@Getter
@Setter
public class SharedCalendarEntity extends SubjectScopedBaseEntity {

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "equipment_id")
  private UUID equipmentId;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "color")
  private String color;

  @Column(name = "default_view")
  private TabDefaultView defaultView;

  @Column(name = "is_announcement_only", nullable = false)
  private boolean announcementOnly;

  @Column(name = "deleted_at")
  private OffsetDateTime deletedAt;
}
