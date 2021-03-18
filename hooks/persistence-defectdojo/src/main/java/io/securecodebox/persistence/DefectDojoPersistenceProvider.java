/*
 *
 *  SecureCodeBox (SCB)
 *  Copyright 2015-2020 iteratec GmbH
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  	http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * /
 */
package io.securecodebox.persistence;

import io.securecodebox.persistence.defectdojo.config.DefectDojoConfig;
import io.securecodebox.persistence.defectdojo.service.EndpointService;
import io.securecodebox.persistence.mapping.DefectDojoFindingToSecureCodeBoxMapper;
import io.securecodebox.persistence.models.Scan;
import io.securecodebox.persistence.service.KubernetesService;
import io.securecodebox.persistence.service.S3Service;
import io.securecodebox.persistence.strategies.VersionedEngagementsStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.stream.Collectors;

public class DefectDojoPersistenceProvider {
  private static final Logger LOG = LoggerFactory.getLogger(DefectDojoPersistenceProvider.class);

  public static void main(String[] args) throws Exception {
    LOG.info("Starting DefectDojo persistence provider");

    // Parse Hook Args passed via command line flags
    if (args == null) {
      throw new RuntimeException("Received `null` as command line flags. Expected exactly four (RawResult & Finding Up/Download Urls)");
    } else if(args.length != 4) {
      LOG.error("Received unexpected command line arguments: {}", List.of(args));
      throw new RuntimeException("DefectDojo Hook received a unexpected number of command line flags. Expected exactly four (RawResult & Finding Up/Download Urls)");
    }
    // RawResult Download Url is passed as the first command line arg
    String rawResultDownloadUrl = args[0];
    // RawResult Upload Url is passed as the forth command line arg
    String findingUploadUrl = args[3];

    var s3Service = new S3Service();
    var kubernetesService = new KubernetesService();
    kubernetesService.init();

    var scan = new Scan(kubernetesService.getScanFromKubernetes());
    scan.validate();

    var config = DefectDojoConfig.fromEnv();

    LOG.info("Downloading Scan Report (RawResults)");
    var rawResults = s3Service.downloadRawResults(rawResultDownloadUrl);
    LOG.info("Finished Downloading Scan Report (RawResults)");
    LOG.debug("RawResults: {}", rawResults);

    LOG.info("Uploading Findings to DefectDojo at: {}", config.getUrl());

    var defectdojoImportStrategy = new VersionedEngagementsStrategy();
    defectdojoImportStrategy.init(config);
    var defectDojoFindings = defectdojoImportStrategy.run(scan, rawResults);

    LOG.info("Identified total Number of findings in DefectDojo: {}", defectDojoFindings.size());

    var endpointService = new EndpointService(config);
    var mapper = new DefectDojoFindingToSecureCodeBoxMapper(config, endpointService);

    var findings = defectDojoFindings.stream()
      .map(mapper::fromDefectDojoFining)
      .collect(Collectors.toList());

    LOG.debug("Mapped Findings: {}", findings);

    s3Service.overwriteFindings(findingUploadUrl, findings);
  }
}
