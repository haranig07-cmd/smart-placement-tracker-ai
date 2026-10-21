package com.sptai.backend;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sptai.backend.service.UserService;
import com.sptai.backend.service.ResumeAnalysisService;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.sptai.backend.entity.Application;
import com.sptai.backend.entity.Job;
import com.sptai.backend.entity.Resume;
import com.sptai.backend.entity.StudentProfile;
import com.sptai.backend.entity.User;
import com.sptai.backend.repository.ApplicationRepository;
import com.sptai.backend.repository.JobRepository;
import com.sptai.backend.repository.ResumeRepository;
import com.sptai.backend.repository.StudentProfileRepository;

@SpringBootTest
@AutoConfigureMockMvc
class BackendApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserService userService;

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private ApplicationRepository applicationRepository;

	@Autowired
	private ResumeRepository resumeRepository;

	@Autowired
	private StudentProfileRepository studentProfileRepository;

	@MockitoBean
	private ResumeAnalysisService resumeAnalysisService;

	private final List<Path> resumeTestFiles = new ArrayList<>();

	@Test
	void contextLoads() {
	}

	@Test
	@Transactional
	void logoutInvalidatesStudentSession() throws Exception {
		User student = createUser("STUDENT", "");
		assertLogoutInvalidatesSession(
				student,
				"/api/applications/student/" + student.getId());
	}

	@Test
	@Transactional
	void logoutInvalidatesCompanySession() throws Exception {
		User company = createUser("COMPANY", "Logout Test Company");
		Job job = createJob(company.getCompanyName());
		assertLogoutInvalidatesSession(
				company,
				"/api/applications/job/" + job.getId());
	}

	@Test
	@Transactional
	void logoutInvalidatesOfficerSession() throws Exception {
		User officer = createUser("OFFICER", "");
		assertLogoutInvalidatesSession(officer, "/api/officer/dashboard");
	}

	@Test
	void anonymousLogoutDoesNotCreateSessionAndPublicJobsRemainAvailable() throws Exception {
		MvcResult logoutResult = mockMvc.perform(post("/api/users/logout"))
			.andExpect(status().isOk())
			.andExpect(content().string("Logged out successfully"))
			.andReturn();

		Assertions.assertNull(logoutResult.getRequest().getSession(false));

		mockMvc.perform(get("/api/jobs"))
			.andExpect(status().isOk());
	}

	@Test
	@Transactional
	void applicationEndpointsRequireSession() throws Exception {
		String applicationRequest = "{\"studentId\":1,\"jobId\":1}";
	String statusRequest = "{\"status\":\"Shortlisted\"}";

		mockMvc.perform(post("/api/applications")
				.contentType(MediaType.APPLICATION_JSON)
				.content(applicationRequest))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Authentication required"));

		mockMvc.perform(get("/api/applications/student/1"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Authentication required"));

		mockMvc.perform(get("/api/applications/job/1"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Authentication required"));

		mockMvc.perform(get("/api/applications/1"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Authentication required"));

		mockMvc.perform(put("/api/applications/1/status")
				.contentType(MediaType.APPLICATION_JSON)
				.content(statusRequest))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Authentication required"));

		mockMvc.perform(put("/api/applications/1/ats-score"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Authentication required"));

		mockMvc.perform(get("/api/company/dashboard/Company%20A/applications"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Authentication required"));

		mockMvc.perform(get("/api/company/dashboard/Company%20A/applications/detail"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Authentication required"));

		mockMvc.perform(get("/api/company/dashboard/Company%20A/shortlisted"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Authentication required"));
	}

	@Test
	@Transactional
	void studentsCanApplyAndReadOnlyTheirOwnApplications() throws Exception {
		User studentA = createUser("STUDENT", "");
User studentB = createUser("STUDENT", "");

StudentProfile studentProfile = new StudentProfile();
studentProfile.setUser(studentA);
studentProfile.setPhone("9876543210");
studentProfile.setDepartment("CSE");
studentProfile.setCollege("VEMU Institute of Technology");
studentProfile.setCgpa("9.89");
studentProfile.setSkills("Java, Python, SQL");
studentProfile.setGraduationYear("2027");

studentProfileRepository.saveAndFlush(studentProfile);

User company = createUser("COMPANY", "Application Test Company");
		Job job = createJob(company.getCompanyName());
		Job anotherJob = createJob(company.getCompanyName());
		Application studentBApplication = createApplication(studentB, anotherJob);
		MockHttpSession studentSession = login(studentA);

		Resume resume = new Resume();
		resume.setUser(studentA);
		resume.setAtsScore(87);
		resumeRepository.saveAndFlush(resume);

		String ownRequest = applicationRequest(studentA.getId(), job.getId());
		MvcResult applyResult = mockMvc.perform(post("/api/applications")
				.session(studentSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(ownRequest))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.studentId").value(studentA.getId()))
			.andExpect(jsonPath("$.status").value("Applied"))
			.andReturn();

		Application ownApplication = applicationRepository
				.findByStudentIdAndJobId(studentA.getId(), job.getId())
				.orElseThrow();
		Assertions.assertEquals(87, ownApplication.getAtsScore());
		Assertions.assertNotNull(ownApplication.getAppliedAt());

		mockMvc.perform(post("/api/applications")
				.session(studentSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(ownRequest))
			.andExpect(status().isBadRequest())
			.andExpect(content().string("You have already applied for this job"));

		mockMvc.perform(post("/api/applications")
				.session(studentSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(applicationRequest(studentB.getId(), job.getId())))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Application access denied"));

		Assertions.assertTrue(applicationRepository
				.findByStudentIdAndJobId(studentB.getId(), job.getId()).isEmpty());

		mockMvc.perform(get("/api/applications/student/{studentId}", studentA.getId())
				.session(studentSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].job.id").value(job.getId()));

		mockMvc.perform(get("/api/applications/student/{studentId}", studentB.getId())
				.session(studentSession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Application access denied"));

		mockMvc.perform(get("/api/applications/{id}", ownApplication.getId())
				.session(studentSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(ownApplication.getId()));

		mockMvc.perform(get("/api/applications/{id}", studentBApplication.getId())
				.session(studentSession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Application access denied"));

		mockMvc.perform(put("/api/applications/{id}/status", studentBApplication.getId())
				.session(studentSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"Selected\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Company access required"));

		mockMvc.perform(put("/api/applications/{id}/ats-score", ownApplication.getId())
				.session(studentSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.atsScore").value(87));

		Assertions.assertEquals(1, applicationRepository.findByStudentId(studentA.getId()).size());
		Assertions.assertTrue(applicationRepository
				.findByStudentIdAndJobId(studentB.getId(), job.getId()).isEmpty());
		Assertions.assertNull(applicationRepository.findById(studentBApplication.getId())
				.orElseThrow().getStatus());
		Assertions.assertNotNull(applyResult.getResponse().getContentAsString());
	}

	@Test
	@Transactional
	void companiesCanViewAndManageOnlyTheirOwnApplications() throws Exception {
		User studentA = createUser("STUDENT", "");
		User studentB = createUser("STUDENT", "");
		User companyA = createUser("COMPANY", "Application Company A");
		User companyB = createUser("COMPANY", "Application Company B");
		Job jobA = createJob(companyA.getCompanyName());
		Job jobB = createJob(companyB.getCompanyName());
		Application applicationA = createApplication(studentA, jobA);
		Application applicationB = createApplication(studentB, jobB);
		applicationA.setStatus("Applied");
		applicationB.setStatus("Applied");
		applicationRepository.saveAndFlush(applicationA);
		applicationRepository.saveAndFlush(applicationB);
		MockHttpSession companyASession = login(companyA);
		MockHttpSession companyBSession = login(companyB);

		mockMvc.perform(get("/api/applications/job/{jobId}", jobA.getId())
				.session(companyASession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(applicationA.getId()));

		mockMvc.perform(get("/api/applications/job/{jobId}", jobB.getId())
				.session(companyASession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Application access denied"));

		mockMvc.perform(get("/api/applications/{id}", applicationA.getId())
				.session(companyASession))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/applications/{id}", applicationB.getId())
				.session(companyASession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Application access denied"));

		mockMvc.perform(get("/api/company/dashboard/{companyName}/applications",
				companyA.getCompanyName()).session(companyASession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(applicationA.getId()));

		mockMvc.perform(get("/api/company/dashboard/{companyName}/applications",
				companyB.getCompanyName()).session(companyASession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Application access denied"));

		mockMvc.perform(get("/api/company/dashboard/{companyName}/applications/detail",
				companyA.getCompanyName()).session(companyASession))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/company/dashboard/{companyName}/applications/detail",
				companyB.getCompanyName()).session(companyASession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Application access denied"));

		mockMvc.perform(get("/api/company/dashboard/{companyName}/shortlisted",
				companyA.getCompanyName()).session(companyASession))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/company/dashboard/{companyName}/shortlisted",
				companyB.getCompanyName()).session(companyASession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Application access denied"));

		mockMvc.perform(put("/api/applications/{id}/status", applicationA.getId())
				.session(companyASession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"Shortlisted\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("Shortlisted"));

		String[] deniedStatuses = {"Shortlisted", "Selected", "Rejected"};
		for (String deniedStatus : deniedStatuses) {
			mockMvc.perform(put("/api/applications/{id}/status?companyName={companyName}",
					applicationB.getId(), companyB.getCompanyName())
					.session(companyASession)
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"status\":\"" + deniedStatus + "\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error").value("Application access denied"));
		}

		mockMvc.perform(put("/api/applications/{id}/status?companyName={companyName}",
				applicationA.getId(), companyB.getCompanyName())
				.session(companyBSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"Selected\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Application access denied"));

		Assertions.assertEquals("Shortlisted",
				applicationRepository.findById(applicationA.getId()).orElseThrow().getStatus());
		Assertions.assertEquals("Applied",
				applicationRepository.findById(applicationB.getId()).orElseThrow().getStatus());
	}

	@Test
	@Transactional
	void officerCannotAccessApplicationEndpoints() throws Exception {
		User student = createUser("STUDENT", "");
		User company = createUser("COMPANY", "Application Test Company");
		User officer = createUser("OFFICER", "");
		Job job = createJob(company.getCompanyName());
		Application application = createApplication(student, job);
		MockHttpSession officerSession = login(officer);

		mockMvc.perform(post("/api/applications")
				.session(officerSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(applicationRequest(student.getId(), job.getId())))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Student access required"));

		mockMvc.perform(get("/api/applications/student/{studentId}", student.getId())
				.session(officerSession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Student access required"));

		mockMvc.perform(get("/api/applications/job/{jobId}", job.getId())
				.session(officerSession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Company access required"));

		mockMvc.perform(get("/api/applications/{id}", application.getId())
				.session(officerSession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Application access denied"));

		mockMvc.perform(put("/api/applications/{id}/status", application.getId())
				.session(officerSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"Selected\"}"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Company access required"));

		mockMvc.perform(put("/api/applications/{id}/ats-score", application.getId())
				.session(officerSession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Student access required"));

		mockMvc.perform(get("/api/company/dashboard/{companyName}/applications",
				company.getCompanyName()).session(officerSession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Company access required"));

		mockMvc.perform(get("/api/company/dashboard/{companyName}/applications/detail",
				company.getCompanyName()).session(officerSession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Company access required"));

		mockMvc.perform(get("/api/company/dashboard/{companyName}/shortlisted",
				company.getCompanyName()).session(officerSession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Company access required"));

		Assertions.assertNull(
				applicationRepository.findById(application.getId()).orElseThrow().getStatus());
	}

	@Test
	@Transactional
	void jobBrowsingRemainsPublic() throws Exception {
		Job job = createJob("Public Job Browse Company");

		mockMvc.perform(get("/api/jobs"))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/jobs/{id}", job.getId()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(job.getId()));
	}

	@Test
	@Transactional
	void jobMutationsRequireCompanySession() throws Exception {
		Job job = createJob("Authorization Test Company");
		User student = createUser("STUDENT", "");
		User officer = createUser("OFFICER", "");
		String request = jobRequest("Authorization Test Company", "Job title");

		assertJobMutationDenied(job.getId(), request, null, "Authentication required");
		assertJobMutationDenied(job.getId(), request, login(student), "Company access required");
		assertJobMutationDenied(job.getId(), request, login(officer), "Company access required");
	}

	@Test
	@Transactional
	void companyJobOwnershipComesFromSessionNotRequestData() throws Exception {
		User companyA = createUser("COMPANY", "Job Test Company A");
		User companyB = createUser("COMPANY", "Job Test Company B");
		Job companyAJob = createJob(companyA.getCompanyName());
		Job companyBJob = createJob(companyB.getCompanyName());
		MockHttpSession companyASession = login(companyA);
		MockHttpSession companyBSession = login(companyB);

		mockMvc.perform(post("/api/jobs")
				.session(companyASession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jobRequest(
						companyBJob.getId(),
						companyB.getCompanyName(),
						"Spoofed Job")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.companyName").value(companyA.getCompanyName()));

		Job createdJob = jobRepository.findByCompanyNameIgnoreCase(companyA.getCompanyName())
				.stream()
				.filter(job -> "Spoofed Job".equals(job.getTitle()))
				.findFirst()
				.orElseThrow();
		Assertions.assertTrue(jobRepository.findByCompanyNameIgnoreCase(companyB.getCompanyName())
				.stream()
				.noneMatch(job -> "Spoofed Job".equals(job.getTitle())));
		Job companyBJobAfterPost = jobRepository.findById(companyBJob.getId()).orElseThrow();
		Assertions.assertEquals(companyB.getCompanyName(), companyBJobAfterPost.getCompanyName());
		Assertions.assertEquals("Resume Test Role", companyBJobAfterPost.getTitle());

		mockMvc.perform(put("/api/jobs/{id}?companyName={companyName}",
				companyAJob.getId(), companyB.getCompanyName())
				.session(companyASession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jobRequest(companyB.getCompanyName(), "Updated by A")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Updated by A"))
			.andExpect(jsonPath("$.companyName").value(companyA.getCompanyName()));

		mockMvc.perform(put("/api/jobs/{id}?companyName={companyName}",
				companyAJob.getId(), companyA.getCompanyName())
				.session(companyBSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(jobRequest(companyA.getCompanyName(), "Unauthorized update")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Job access denied"));

		mockMvc.perform(delete("/api/jobs/{id}?companyName={companyName}",
				companyAJob.getId(), companyA.getCompanyName())
				.session(companyBSession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Job access denied"));

		Job unchangedJob = jobRepository.findById(companyAJob.getId()).orElseThrow();
		Assertions.assertEquals("Updated by A", unchangedJob.getTitle());

		mockMvc.perform(delete("/api/jobs/{id}?companyName={companyName}",
				companyAJob.getId(), companyB.getCompanyName())
				.session(companyASession))
			.andExpect(status().isOk());

		Assertions.assertTrue(jobRepository.findById(companyAJob.getId()).isEmpty());
		Assertions.assertEquals(companyA.getCompanyName(), createdJob.getCompanyName());
	}

	private void assertJobMutationDenied(
			Long jobId,
			String requestBody,
			MockHttpSession session,
			String error) throws Exception {

		var createRequest = post("/api/jobs")
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody);
		var updateRequest = put("/api/jobs/{id}", jobId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody);
		var deleteRequest = delete("/api/jobs/{id}", jobId);

		if (session != null) {
			createRequest.session(session);
			updateRequest.session(session);
			deleteRequest.session(session);
		}

		mockMvc.perform(createRequest)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value(error));

		mockMvc.perform(updateRequest)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value(error));

		mockMvc.perform(deleteRequest)
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value(error));
	}

	@Test
	@Transactional
	void studentProfileEndpointsRequireSession() throws Exception {
		long userId = 123L;
		String profile = "{\"user\":{\"email\":\"student@example.test\"},\"phone\":\"555\"}";

		mockMvc.perform(get("/api/student-profile/{userId}", userId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Authentication required"));

		mockMvc.perform(post("/api/student-profile/{userId}", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(profile))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Authentication required"));

		mockMvc.perform(put("/api/student-profile/{userId}", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(profile))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Authentication required"));
	}

	@Test
	@Transactional
	void studentCanReadAndUpdateOnlyOwnProfile() throws Exception {
		User studentA = createUser("STUDENT", "");
		User studentB = createUser("STUDENT", "");
		StudentProfile profileA = createStudentProfile(studentA, "A initial");
		StudentProfile profileB = createStudentProfile(studentB, "B unchanged");
		MockHttpSession studentSession = login(studentA);

		mockMvc.perform(get("/api/student-profile/{userId}", studentA.getId())
				.session(studentSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phone").value("A initial"));

		String identityAttack = profileRequest(studentB, "A updated");
		mockMvc.perform(put("/api/student-profile/{userId}", studentA.getId())
				.session(studentSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(identityAttack))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.id").value(studentA.getId()));

		mockMvc.perform(get("/api/student-profile/{userId}", studentB.getId())
				.session(studentSession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Profile access denied"));

		mockMvc.perform(put("/api/student-profile/{userId}", studentB.getId())
				.session(studentSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(profileRequest(studentB, "B hacked")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Profile access denied"));

		Assertions.assertEquals("A updated",
				studentProfileRepository.findByUserId(studentA.getId()).orElseThrow().getPhone());
		Assertions.assertEquals("B unchanged",
				studentProfileRepository.findByUserId(studentB.getId()).orElseThrow().getPhone());
		Assertions.assertEquals(studentB.getId(), profileB.getUser().getId());
		Assertions.assertEquals(studentA.getId(), profileA.getUser().getId());
	}

	@Test
	@Transactional
	void studentCanCreateOnlyOwnProfile() throws Exception {
		User studentA = createUser("STUDENT", "");
		User studentB = createUser("STUDENT", "");
		MockHttpSession studentSession = login(studentA);

		mockMvc.perform(post("/api/student-profile/{userId}", studentA.getId())
				.session(studentSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(profileRequest(studentA, "A profile")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.user.id").value(studentA.getId()));

		mockMvc.perform(post("/api/student-profile/{userId}", studentB.getId())
				.session(studentSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(profileRequest(studentB, "B profile")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Profile access denied"));

		Assertions.assertTrue(studentProfileRepository.findByUserId(studentB.getId()).isEmpty());
	}

	@Test
	@Transactional
	void companyCanReadOnlyProfilesOfItsApplicants() throws Exception {
		User studentA = createUser("STUDENT", "");
		User studentB = createUser("STUDENT", "");
		User companyA = createUser("COMPANY", "Profile Test Company A");
		User companyB = createUser("COMPANY", "Profile Test Company B");
		StudentProfile profileA = createStudentProfile(studentA, "A phone");
		StudentProfile profileB = createStudentProfile(studentB, "B phone");
		Job jobA = createJob(companyA.getCompanyName());
		Job jobB = createJob(companyB.getCompanyName());
		createApplication(studentA, jobA);
		createApplication(studentB, jobB);
		MockHttpSession companySession = login(companyA);

		mockMvc.perform(get("/api/student-profile/{userId}", studentA.getId())
				.session(companySession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.phone").value("A phone"));

		mockMvc.perform(get("/api/student-profile/{userId}", studentB.getId())
				.session(companySession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Profile access denied"));

		mockMvc.perform(put("/api/student-profile/{userId}", studentA.getId())
				.session(companySession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(profileRequest(studentA, "A changed by company")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Profile access denied"));

		Assertions.assertEquals("A phone", profileA.getPhone());
		Assertions.assertEquals("B phone", profileB.getPhone());
	}

	@Test
	@Transactional
	void officerCannotAccessStudentProfileController() throws Exception {
		User student = createUser("STUDENT", "");
		User officer = createUser("OFFICER", "");
		StudentProfile profile = createStudentProfile(student, "unchanged");
		MockHttpSession officerSession = login(officer);

		mockMvc.perform(get("/api/student-profile/{userId}", student.getId())
				.session(officerSession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Profile access denied"));

		mockMvc.perform(put("/api/student-profile/{userId}", student.getId())
				.session(officerSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(profileRequest(student, "changed by officer")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Profile access denied"));

		Assertions.assertEquals("unchanged", profile.getPhone());
	}

	@Test
	@Transactional
	void resumeEndpointsRequireSession() throws Exception {
		long userId = 123L;
		MockMultipartFile file = resumeUploadFile();

		mockMvc.perform(get("/api/resume/{userId}", userId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Authentication required"));

		mockMvc.perform(get("/api/resume/{userId}/file", userId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Authentication required"));

		mockMvc.perform(multipart("/api/resume/{userId}/upload", userId)
				.file(file))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Authentication required"));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete("/api/resume/{userId}", userId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Authentication required"));
	}

	@Test
	@Transactional
	void resumeViewAuthorizationFollowsSessionRoleAndApplications() throws Exception {
		User studentA = createUser("STUDENT", "");
		User studentB = createUser("STUDENT", "");
		User companyA = createUser("COMPANY", "Resume Test Company A");
		User companyB = createUser("COMPANY", "Resume Test Company B");
		User officer = createUser("OFFICER", "");
		Resume resumeA = createResume(studentA);
		createResume(studentB);
		Job companyAJob = createJob(companyA.getCompanyName());
		Job companyBJob = createJob(companyB.getCompanyName());
		createApplication(studentA, companyAJob);
		createApplication(studentB, companyBJob);

		MockHttpSession studentASession = login(studentA);
		MockHttpSession companyASession = login(companyA);
		MockHttpSession officerSession = login(officer);

		mockMvc.perform(get("/api/resume/{userId}", studentA.getId())
				.session(studentASession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fileName").value(resumeA.getFileName()));

		mockMvc.perform(get("/api/resume/{userId}/file", studentA.getId())
				.session(studentASession))
			.andExpect(status().isOk())
			.andExpect(content().bytes(Files.readAllBytes(Path.of(resumeA.getFilePath()))));

		mockMvc.perform(get("/api/resume/{userId}/file", studentB.getId())
				.session(studentASession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Resume access denied"));

		mockMvc.perform(get("/api/resume/{userId}/file", studentA.getId())
				.session(companyASession))
			.andExpect(status().isOk());

		mockMvc.perform(get("/api/resume/{userId}/file", studentB.getId())
				.session(companyASession))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Resume access denied"));

		mockMvc.perform(get("/api/resume/{userId}/file", studentB.getId())
				.session(officerSession))
			.andExpect(status().isOk());
	}

	@Test
	@Transactional
	void studentCanDeleteOnlyOwnResume() throws Exception {
		User studentA = createUser("STUDENT", "");
		User studentB = createUser("STUDENT", "");
		Resume resumeA = createResume(studentA);
		Resume resumeB = createResume(studentB);
		MockHttpSession session = login(studentA);

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete("/api/resume/{userId}", studentB.getId())
				.session(session))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Resume access denied"));

		Assertions.assertTrue(resumeRepository.findByUserId(studentB.getId()).isPresent());
		Assertions.assertTrue(Files.exists(Path.of(resumeB.getFilePath())));

		mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.delete("/api/resume/{userId}", studentA.getId())
				.session(session))
			.andExpect(status().isOk());

		Assertions.assertTrue(resumeRepository.findByUserId(studentA.getId()).isEmpty());
		Assertions.assertFalse(Files.exists(Path.of(resumeA.getFilePath())));
	}

	@Test
	@Transactional
	void studentUploadUsesAuthenticatedIdentity() throws Exception {
		User studentA = createUser("STUDENT", "");
		User studentB = createUser("STUDENT", "");
		MockHttpSession session = login(studentA);
		when(resumeAnalysisService.analyzeResume(anyString()))
				.thenReturn(Map.of("atsScore", 86));

		mockMvc.perform(multipart("/api/resume/{userId}/upload", studentB.getId())
				.file(resumeUploadFile())
				.session(session))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.error").value("Resume access denied"));

		Assertions.assertTrue(resumeRepository.findByUserId(studentB.getId()).isEmpty());

		mockMvc.perform(multipart("/api/resume/{userId}/upload", studentA.getId())
				.file(resumeUploadFile())
				.session(session))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.fileName").value("candidate.pdf"));

		Resume uploadedResume = resumeRepository.findByUserId(studentA.getId()).orElseThrow();
		resumeTestFiles.add(Path.of(uploadedResume.getFilePath()));
	}

	@AfterEach
	void removeResumeTestFiles() throws IOException {
		for (Path file : resumeTestFiles) {
			Files.deleteIfExists(file);
		}
		resumeTestFiles.clear();
	}

	@Test
	@Transactional
	void studentRegistrationSucceeds() throws Exception {
		String email = registrationEmail("student");

		mockMvc.perform(post("/api/users/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationRequest(email, "STUDENT", "")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.role").value("STUDENT"));
	}

	@Test
	@Transactional
	void companyRegistrationSucceeds() throws Exception {
		String email = registrationEmail("company");

		mockMvc.perform(post("/api/users/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registrationRequest(email, "COMPANY", "Feature Test Company")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.role").value("COMPANY"))
			.andExpect(jsonPath("$.companyName").value("Feature Test Company"));
	}

	
@Test
@Transactional
void unauthorizedOfficerRegistrationIsRejected() throws Exception {
    String email = registrationEmail("officer");

    mockMvc.perform(post("/api/users/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(registrationRequest(email, "OFFICER", "")))
        .andExpect(status().isForbidden());

    Assertions.assertTrue(userService.findByEmail(email).isEmpty());
}

	
@Test
@Transactional
void duplicateEmailRegistrationBehaviorIsPreserved() throws Exception {
    String email = registrationEmail("duplicate");
    String request = registrationRequest(email, "STUDENT", "");

    mockMvc.perform(post("/api/users/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/users/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Email already registered"));
}


	@Test
	@Transactional
	void officerEndpointsRequireOfficerSession() throws Exception {
		String[] officerEndpoints = {
				"/api/officer/dashboard",
				"/api/officer/students",
				"/api/officer/companies",
				"/api/officer/drives",
				"/api/officer/drives/999999/eligible-students",
				"/api/officer/reports"
		};

		User student = createUser("STUDENT", "");
		User company = createUser("COMPANY", "Feature Test Company");
		User officer = createUser("OFFICER", "");

		assertOfficerEndpointsForbidden(officerEndpoints, null);
		assertOfficerEndpointsForbidden(officerEndpoints, new MockHttpSession());
		assertOfficerEndpointsForbidden(officerEndpoints, login(student));
		assertOfficerEndpointsForbidden(officerEndpoints, login(company));

		MockHttpSession officerSession = login(officer);
		for (String endpoint : officerEndpoints) {
			mockMvc.perform(get(endpoint).session(officerSession))
					.andExpect(status().isOk())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
		}
	}

	private void assertOfficerEndpointsForbidden(
			String[] endpoints,
			MockHttpSession session) throws Exception {

		for (String endpoint : endpoints) {
			var request = get(endpoint);
			if (session != null) {
				request.session(session);
			}

			mockMvc.perform(request)
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.error").value("Officer access required"));
		}
	}

	@Test
	@Transactional
	void applicationStatusFlowsFromCompanyToStudent() throws Exception {
		User student = createUser("STUDENT", "");
		User anotherStudent = createUser("STUDENT", "");
		StudentProfile studentProfile = new StudentProfile();
studentProfile.setUser(student);
studentProfile.setPhone("9876543210");
studentProfile.setDepartment("CSE");
studentProfile.setCollege("VEMU Institute of Technology");
studentProfile.setCgpa("9.89");
studentProfile.setSkills("Java, Python, SQL");
studentProfile.setGraduationYear("2027");
studentProfileRepository.saveAndFlush(studentProfile);
		User company = createUser("COMPANY", "Feature Test Company");
		User anotherCompany = createUser("COMPANY", "Another Test Company");
		Resume studentResume = createResume(student);
studentResume.setAtsScore(87);
resumeRepository.saveAndFlush(studentResume);
		MockHttpSession studentSession = login(student);
		MockHttpSession anotherStudentSession = login(anotherStudent);
		MockHttpSession companySession = login(company);
		MockHttpSession anotherCompanySession = login(anotherCompany);

		Job job = new Job();
		job.setTitle("Software Engineer");
		job.setCompanyName(company.getCompanyName());
		job.setLocation("Remote");
		jobRepository.saveAndFlush(job);

		String applyRequest = "{\"studentId\":" + student.getId() +
				",\"jobId\":" + job.getId() + "}";

		mockMvc.perform(post("/api/applications")
				.session(studentSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(applyRequest))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("Applied"));

		Application application = applicationRepository
				.findByStudentIdAndJobId(student.getId(), job.getId())
				.orElseThrow();

		mockMvc.perform(get("/api/company/dashboard/{companyName}/applications/detail",
					company.getCompanyName())
				.session(companySession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].student.name").value(student.getName()))
			.andExpect(jsonPath("$[0].status").value("Applied"));

		mockMvc.perform(get("/api/company/dashboard/{companyName}/applications/detail",
					company.getCompanyName())
				.session(anotherCompanySession))
			.andExpect(status().isForbidden());

		String updateRequest = "{\"status\":\"Shortlisted\"}";
		mockMvc.perform(put("/api/applications/{id}/status", application.getId())
				.session(anotherCompanySession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateRequest))
			.andExpect(status().isForbidden());

		mockMvc.perform(put("/api/applications/{id}/status", application.getId())
				.session(companySession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"Interview\"}"))
			.andExpect(status().isBadRequest());

		mockMvc.perform(put("/api/applications/{id}/status", application.getId())
				.session(companySession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateRequest))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("Shortlisted"));

		mockMvc.perform(get("/api/applications/student/{studentId}", student.getId())
				.session(studentSession))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].status").value("Shortlisted"));

		mockMvc.perform(post("/api/applications")
				.session(studentSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content(applyRequest))
			.andExpect(status().isBadRequest());

		mockMvc.perform(get("/api/applications/student/{studentId}", company.getId())
				.session(companySession))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/applications/student/{studentId}", student.getId())
				.session(anotherStudentSession))
			.andExpect(status().isForbidden());

		Assertions.assertEquals("Shortlisted", application.getStatus());
	}

	private void assertLogoutInvalidatesSession(User user, String protectedEndpoint)
			throws Exception {
		MockHttpSession session = login(user);

		mockMvc.perform(get(protectedEndpoint).session(session))
			.andExpect(status().isOk());

		mockMvc.perform(post("/api/users/logout").session(session))
			.andExpect(status().isOk())
			.andExpect(content().string("Logged out successfully"));

		mockMvc.perform(get(protectedEndpoint).session(session))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/jobs"))
			.andExpect(status().isOk());
	}

	private String applicationRequest(Long studentId, Long jobId) {
		return "{\"studentId\":" + studentId + ",\"jobId\":" + jobId + "}";
	}

	private String jobRequest(String companyName, String title) {
		return jobRequest(null, companyName, title);
	}

	private String jobRequest(Long id, String companyName, String title) {
		return "{\"id\":" + (id == null ? "null" : id) +
				",\"title\":\"" + title +
				"\",\"companyName\":\"" + companyName +
				"\",\"location\":\"Remote\",\"jobType\":\"Full-time\"," +
				"\"experience\":\"Any\",\"salary\":\"100000\",\"deadline\":null," +
				"\"skills\":\"Java\",\"description\":\"Test job description\"}";
	}

	private StudentProfile createStudentProfile(User user, String phone) {
		StudentProfile profile = new StudentProfile();
		profile.setUser(user);
		profile.setPhone(phone);
	profile.setDepartment("Computer Science");
		profile.setCollege("Test College");
		profile.setCgpa("8.0");
		profile.setSkills("Java, SQL");
		profile.setGraduationYear("2027");
		return studentProfileRepository.saveAndFlush(profile);
	}

	private String profileRequest(User bodyUser, String phone) {
		return "{\"user\":{\"id\":" + bodyUser.getId() +
				",\"email\":\"" + bodyUser.getEmail() +
				"\"},\"phone\":\"" + phone +
				"\",\"department\":\"Computer Science\",\"college\":\"Test College\"," +
				"\"cgpa\":\"8.0\",\"skills\":\"Java, SQL\",\"graduationYear\":\"2027\"}";
	}

	private Resume createResume(User user) throws IOException {
		Path file = Files.createTempFile("sptai-resume-test-", ".pdf");
		Files.write(file, "resume test".getBytes(StandardCharsets.UTF_8));
		resumeTestFiles.add(file);

		Resume resume = new Resume();
		resume.setUser(user);
		resume.setFileName("resume.pdf");
		resume.setFilePath(file.toString());
		return resumeRepository.saveAndFlush(resume);
	}

	private Job createJob(String companyName) {
		Job job = new Job();
		job.setTitle("Resume Test Role");
		job.setCompanyName(companyName);
		return jobRepository.saveAndFlush(job);
	}

	private Application createApplication(User student, Job job) {
		Application application = new Application();
		application.setStudent(student);
		application.setJob(job);
		return applicationRepository.saveAndFlush(application);
	}

	private MockMultipartFile resumeUploadFile() {
		return new MockMultipartFile(
				"file",
				"candidate.pdf",
				"application/pdf",
				"test resume".getBytes(StandardCharsets.UTF_8));
	}

	private User createUser(String role, String companyName) {
		User user = new User();
		user.setName("Feature Test " + role);
		if ("OFFICER".equalsIgnoreCase(role)) {
    user.setEmail("haranig650@gmail.com");
} else {
    user.setEmail("feature-test-" + role.toLowerCase() + "-" +
            System.nanoTime() + "@example.test");
}
		// Set plaintext here; UserService.registerUser will BCrypt-hash it
		user.setPassword("Test@12345");
		user.setRole(role);
		user.setCompanyName(companyName);
		// Register via service so password is hashed before login
		return userService.registerUser(user);
	}

	private String registrationEmail(String role) {
		return "registration-test-" + role + "-" + System.nanoTime() + "@example.test";
	}

	private String registrationRequest(String email, String role, String companyName) {
		return "{\"name\":\"Registration Test\",\"email\":\"" + email +
				"\",\"password\":\"Test@12345\",\"role\":\"" + role +
				"\",\"companyName\":\"" + companyName + "\"}";
	}

	private MockHttpSession login(User user) throws Exception {
		// Always use the known plaintext password for login
		String loginRequest = "{\"email\":\"" + user.getEmail() +
				"\",\"password\":\"Test@12345\"}";
		MvcResult result = mockMvc.perform(post("/api/users/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginRequest))
			.andExpect(status().isOk())
			.andReturn();
		return (MockHttpSession) result.getRequest().getSession(false);
	}

}
