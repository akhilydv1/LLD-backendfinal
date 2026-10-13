package com.designloop.repository;

import com.designloop.domain.AttemptDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AttemptRepository extends MongoRepository<AttemptDocument, String> {
    Optional<AttemptDocument> findByIdAndLearnerId(String id, String learnerId);
    List<AttemptDocument> findByLearnerIdOrderByUpdatedAtDesc(String learnerId);
}
