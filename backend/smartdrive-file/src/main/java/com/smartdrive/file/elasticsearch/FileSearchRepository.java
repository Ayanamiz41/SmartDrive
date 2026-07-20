package com.smartdrive.file.elasticsearch;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface FileSearchRepository extends ElasticsearchRepository<FileDocument, String> {
}
