/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaweb.employeerostering.domain.rotation;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.ForeignKey;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.validation.constraints.NotNull;

import org.optaweb.employeerostering.domain.common.AbstractPersistable;
import org.optaweb.employeerostering.domain.shift.Shift;
import org.optaweb.employeerostering.domain.skill.Skill;
import org.optaweb.employeerostering.domain.spot.Spot;

@Entity(name = "Time_Bucket")
public class TimeBucket extends AbstractPersistable {
    private LocalTime startTime;
    private LocalTime endTime;

    @ManyToOne
    private Spot spot;

    @NotNull
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "TimeBucketAdditionalSkillSet",
            joinColumns = @JoinColumn(name = "timebucketId", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "skillId", referencedColumnName = "id"))
    private Set<Skill> additionalSkillSet;

    @ElementCollection
    @CollectionTable(name = "repeat_on_day_set",
            joinColumns = {
                    @JoinColumn(name = "day_id",
                            referencedColumnName = "id",
                            foreignKey = @ForeignKey(name = "DAY_FK",
                                    foreignKeyDefinition = "FOREIGN KEY (day_id) references time_bucket (id)" +
                                            " ON UPDATE NO ACTION ON DELETE CASCADE"))
            })
    private Set<DayOfWeek> repeatOnDaySet;

    @ElementCollection
    @CollectionTable(name = "seat_list",
            joinColumns = {
                    @JoinColumn(name = "seat_id",
                            referencedColumnName = "id",
                            foreignKey = @ForeignKey(name = "SEAT_FK",
                                    foreignKeyDefinition = "FOREIGN KEY (seat_id) references time_bucket (id)" +
                                            " ON UPDATE NO ACTION ON DELETE CASCADE"))
            })
    private List<Seat> seatList;

    public TimeBucket() {
    }

    public TimeBucket(Integer tenantId, Spot spot, LocalTime startTime, LocalTime endTime,
            Set<Skill> additionalSkillSet,
            Set<DayOfWeek> repeatOnDaySet, DayOfWeek startOfWeek, int rotationLength) {
        this(tenantId, spot, startTime, endTime, additionalSkillSet, repeatOnDaySet,
                generateDefaultSeatList(startOfWeek, repeatOnDaySet, rotationLength));
    }

    public TimeBucket(Integer tenantId, Spot spot, LocalTime startTime, LocalTime endTime,
            Set<Skill> additionalSkillSet,
            Set<DayOfWeek> repeatOnDaySet, List<Seat> seatList) {
        super(tenantId);
        this.spot = spot;
        this.startTime = startTime;
        this.endTime = endTime;
        this.additionalSkillSet = additionalSkillSet;
        this.repeatOnDaySet = repeatOnDaySet;
        this.seatList = new ArrayList<>(seatList);
    }

    // ********************************************************************************************
    // Constructor default utils
    // ********************************************************************************************  

    private static List<Seat> generateDefaultSeatList(DayOfWeek startOfWeek, Set<DayOfWeek> repeatOnDaySet,
            int rotationLength) {
        List<Seat> seatList = new ArrayList<>();
        DayOfWeek rotationDay = startOfWeek;
        for (int i = 0; i < rotationLength; i++) {
            if (repeatOnDaySet.contains(rotationDay)) {
                seatList.add(new Seat(i, null));
            }
            rotationDay = rotationDay.plus(1);
        }
        return seatList;
    }

    // ********************************************************************************************
    // Getters and Setters
    // ********************************************************************************************   

    public Spot getSpot() {
        return spot;
    }

    public void setSpot(Spot spot) {
        this.spot = spot;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Set<Skill> getAdditionalSkillSet() {
        return additionalSkillSet;
    }

    public void setAdditionalSkillSet(Set<Skill> additionalSkillSet) {
        this.additionalSkillSet = additionalSkillSet;
    }

    public Set<DayOfWeek> getRepeatOnDaySet() {
        return repeatOnDaySet;
    }

    public void setRepeatOnDaySet(Set<DayOfWeek> repeatOnDaySet) {
        this.repeatOnDaySet = repeatOnDaySet;
    }

    public List<Seat> getSeatList() {
        return seatList;
    }

    public void setSeatList(List<Seat> seatList) {
        this.seatList = seatList;
    }

    public void setValuesFromTimeBucket(TimeBucket updatedTimeBucket) {
        setSpot(updatedTimeBucket.getSpot());
        setStartTime(updatedTimeBucket.getStartTime());
        setEndTime(updatedTimeBucket.getEndTime());
        setAdditionalSkillSet(updatedTimeBucket.getAdditionalSkillSet());
        setRepeatOnDaySet(updatedTimeBucket.getRepeatOnDaySet());
        setSeatList(updatedTimeBucket.getSeatList());
    }

    public Optional<Shift> createShiftForOffset(LocalDate startDate, int offset, ZoneId zoneId,
            boolean defaultToRotationEmployee) {
        return seatList.stream().filter(seat -> seat.getDayInRotation() == offset).findAny().map(seat -> {
            LocalDateTime startDateTime = startDate.atTime(getStartTime());
            LocalDate endDate = (getStartTime().isBefore(getEndTime())) ? startDate : // Ex: 9am - 5pm
            startDate.plusDays(1); // Ex: 9pm - 5am

            LocalDateTime endDateTime = endDate.atTime(getEndTime());

            OffsetDateTime startOffsetDateTime = OffsetDateTime.of(startDateTime,
                    zoneId.getRules().getOffset(startDateTime));
            OffsetDateTime endOffsetDateTime = OffsetDateTime.of(endDateTime,
                    zoneId.getRules().getOffset(endDateTime));

            Shift shift = new Shift(getTenantId(), getSpot(), startOffsetDateTime,
                    endOffsetDateTime, seat.getEmployee(),
                    new HashSet<>(additionalSkillSet), null);

            if (defaultToRotationEmployee) {
                shift.setEmployee(seat.getEmployee());
            }
            return shift;
        });
    }
}
