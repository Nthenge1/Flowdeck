package com.eclectics.collaboration.Tool.repository;

import com.eclectics.collaboration.Tool.model.Label;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LabelRepository extends JpaRepository<Label, Long> {
    List<Label> findByBoardId(Long boardId);
    boolean existsByIdAndBoardId(Long id, Long boardId);
}