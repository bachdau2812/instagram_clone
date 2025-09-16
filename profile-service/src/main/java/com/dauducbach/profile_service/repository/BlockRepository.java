package com.dauducbach.profile_service.repository;

import com.dauducbach.profile_service.entity.Block;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

public interface BlockRepository extends ReactiveCrudRepository<Block, String> {

}
