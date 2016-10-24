package br.com.sonarpreviewbreak.executor;

import java.io.InputStream;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.maven.plugin.logging.Log;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.sonarpreviewbreak.dto.IssuesDTO;
import br.com.sonarpreviewbreak.dto.PreviewDTO;
import br.com.sonarpreviewbreak.dto.QueryAnalysisDTO;
import br.com.sonarpreviewbreak.dto.ResultAnalysisDTO;
import br.com.sonarpreviewbreak.dto.ennumerated.Severity;
import br.com.sonarpreviewbreak.exception.SonarAnalysisException;
import br.com.sonarpreviewbreak.exception.SonarPreviewBreakException;

/**
 * Execute the analysis on project, getting the json preview file generated by
 * sonar:sonar sonar.analysis.mode=preview and
 * sonar.report.export.path=FILE_NAME. All analysis is about report path.
 * 
 * @author Vinicius Antonio Gai
 *
 */
public class AnalysisExecutor {

	private static final String ERROR_MESSAGE_QUALITY_GATES = "Number of {0} severity is greater than {1}. Actual number is {2}";

	private final Log log;

	public AnalysisExecutor(final Log log) {
		this.log = log;
	}

	/**
	 * Process the analysis of sonar.
	 * 
	 * @param queryAnalysisDTO
	 *            Data to process analysis.
	 * @return {@link ResultAnalysisDTO} with result of analysis.
	 * @throws SonarPreviewBreakException
	 */
	public ResultAnalysisDTO processanalysis(final QueryAnalysisDTO queryAnalysisDTO) throws SonarPreviewBreakException {

		return processNewIssues(getAllNewIssues(queryAnalysisDTO.getReportPath()), queryAnalysisDTO);
	}

	/**
	 * Process new issues.
	 * 
	 * @param newIssues
	 *            List of new issues.
	 * @param queryAnalysisDTO
	 *            Data to analysis new issues.
	 * @return {@link ResultAnalysisDTO}.
	 */
	private ResultAnalysisDTO processNewIssues(final List<IssuesDTO> newIssues, final QueryAnalysisDTO queryAnalysisDTO) {

		ResultAnalysisDTO resultAnalysisDTO = ResultAnalysisDTO.createSuccess();

		if (CollectionUtils.isNotEmpty(newIssues)) {
			try {
				analysisQualityGates(newIssues, Severity.BLOCKER, queryAnalysisDTO.getQtdBlockers());
				analysisQualityGates(newIssues, Severity.CRITICAL, queryAnalysisDTO.getQtdVulnerabilities());
				analysisQualityGates(newIssues, Severity.MAJOR, queryAnalysisDTO.getQtdMajors());
				analysisQualityGates(newIssues, Severity.MINOR, queryAnalysisDTO.getQtdMinors());
			} catch (SonarAnalysisException e) {
				log.debug("Error to analisys log.", e);
				resultAnalysisDTO = ResultAnalysisDTO.createError(e.getAnalisysMessage());
			}
		}

		return resultAnalysisDTO;
	}

	/**
	 * analysis new issues.
	 * 
	 * @param newIssues
	 *            List of new issues.
	 * @param severity
	 *            Severity to analysis
	 * @param qtdMaxIssues
	 *            Max numbers of issues
	 * @throws SonarAnalysisException
	 *             Exception throwing when number of issues greater then max
	 *             quantity;
	 */
	private void analysisQualityGates(final List<IssuesDTO> newIssues, final Severity severity, final Integer qtdMaxIssues)
			throws SonarAnalysisException {

		final long quantity = newIssues.stream().filter(issue -> severity.equals(issue.getSeverity())).count();

		if (qtdMaxIssues != null && quantity > qtdMaxIssues.longValue()) {
			throw new SonarAnalysisException(MessageFormat.format(ERROR_MESSAGE_QUALITY_GATES, severity.name(), qtdMaxIssues, quantity));
		}
	}

	/**
	 * Get all new issues.
	 * 
	 * @param jsonFileName
	 *            Name os json to analysis.
	 * @return List of new issues.
	 */
	private List<IssuesDTO> getAllNewIssues(final String jsonFileName) throws SonarPreviewBreakException {

		List<IssuesDTO> newIssuesDTOs = null;

		final PreviewDTO previewDTO = getPreviewDTO(jsonFileName);

		if (previewDTO != null && CollectionUtils.isNotEmpty(previewDTO.getIssues())) {
			newIssuesDTOs = previewDTO.getIssues().stream().filter(issue -> issue.isNew()).distinct().collect(Collectors.toList());
		}

		return newIssuesDTOs;

	}

	/**
	 * Load json file and convert to {@link PreviewDTO}
	 * 
	 * @param jsonFileName
	 *            Json File name.
	 * @return {@link PreviewDTO}
	 * @throws SonarPreviewBreakException
	 */
	private PreviewDTO getPreviewDTO(final String jsonFileName) throws SonarPreviewBreakException {

		try {
			final ObjectMapper mapper = new ObjectMapper();
			mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss"));

			final InputStream jsonFile = AnalysisExecutor.class.getClassLoader().getResourceAsStream(jsonFileName);

			return mapper.readValue(jsonFile, PreviewDTO.class);
		} catch (Exception e) {
			log.error("Erro to convert json to PreviewDTO", e);

			throw new SonarPreviewBreakException("Erro to convert json to PreviewDTO", e);
		}

	}

}