package com.designloop.repository;

import com.designloop.domain.ProblemDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProblemRepository extends MongoRepository<ProblemDocument, String> {
}
