package com.cognologix.fpa;

import com.cognologix.fpa.contracts.ContractStatus;
import com.cognologix.fpa.contracts.DocumentType;
import com.cognologix.fpa.contracts.PaperType;
import com.cognologix.fpa.contracts.VersionStatus;
import com.cognologix.fpa.contracts.domain.Contract;
import com.cognologix.fpa.contracts.domain.ContractDocument;
import com.cognologix.fpa.contracts.domain.ContractTemplate;
import com.cognologix.fpa.contracts.domain.ContractTemplateDocument;
import com.cognologix.fpa.contracts.domain.ContractVersion;
import com.cognologix.fpa.contracts.dto.DocumentMeta;
import com.cognologix.fpa.contracts.dto.TemplateDocumentMeta;
import com.cognologix.fpa.contracts.repository.ContractDocumentRepository;
import com.cognologix.fpa.contracts.repository.ContractRepository;
import com.cognologix.fpa.contracts.repository.ContractTemplateDocumentRepository;
import com.cognologix.fpa.contracts.repository.ContractTemplateRepository;
import com.cognologix.fpa.contracts.repository.ContractTypeRepository;
import com.cognologix.fpa.contracts.repository.ContractVersionRepository;
import com.cognologix.fpa.general.UserRole;
import com.cognologix.fpa.general.UserService;
import com.cognologix.fpa.people.EmployeeRegistry;
import com.cognologix.fpa.people.domain.ImportType;
import com.cognologix.fpa.people.domain.MasterRecord;
import com.cognologix.fpa.people.domain.PayrollSnapshot;
import com.cognologix.fpa.people.domain.Period;
import com.cognologix.fpa.people.domain.PeriodStatus;
import com.cognologix.fpa.people.domain.PeriodVersion;
import com.cognologix.fpa.people.domain.ReconciliationStatus;
import com.cognologix.fpa.people.domain.SnapshotUpload;
import com.cognologix.fpa.people.repository.EmployeeRegistryRepository;
import com.cognologix.fpa.people.repository.MasterRecordRepository;
import com.cognologix.fpa.people.repository.PayrollSnapshotRepository;
import com.cognologix.fpa.people.repository.PeriodRepository;
import com.cognologix.fpa.people.repository.PeriodVersionRepository;
import com.cognologix.fpa.people.repository.SnapshotUploadRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hibernate 7 checks that the earlier Boot 4 compile trial could not run:
 * constructor expressions, {@code integer[]}, and payroll generated columns.
 */
@SpringBootTest
@Testcontainers
@Transactional
class Hibernate7PostgresTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @Autowired EntityManager entityManager;
    @Autowired UserService userService;
    @Autowired ContractTypeRepository contractTypeRepository;
    @Autowired ContractRepository contractRepository;
    @Autowired ContractVersionRepository contractVersionRepository;
    @Autowired ContractDocumentRepository contractDocumentRepository;
    @Autowired ContractTemplateRepository contractTemplateRepository;
    @Autowired ContractTemplateDocumentRepository contractTemplateDocumentRepository;
    @Autowired PeriodRepository periodRepository;
    @Autowired PeriodVersionRepository periodVersionRepository;
    @Autowired SnapshotUploadRepository snapshotUploadRepository;
    @Autowired PayrollSnapshotRepository payrollSnapshotRepository;
    @Autowired EmployeeRegistryRepository employeeRegistryRepository;
    @Autowired MasterRecordRepository masterRecordRepository;

    @Test
    void integerArrayRoundTripsOnContract() {
        var owner = userService.createUser(
                "hibernate7-array@cognologix.com", "Hibernate Seven", UserRole.ADMIN, "Secret123!", "test");
        var type = contractTypeRepository.findByTypeCodeIgnoreCase("NDA").orElseThrow();

        var contract = new Contract();
        contract.setContractNumber("H7-ARRAY");
        contract.setTitle("Array mapping");
        contract.setContractType(type);
        contract.setPaperType(PaperType.THIRD_PARTY);
        contract.setPartyName("Array Party");
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setOwnerUserId(owner.getId());
        contract.setCreatedBy("test");
        contract.setReminderDaysOverride(new Integer[] {30, 14, 7});
        contractRepository.saveAndFlush(contract);
        entityManager.clear();

        Integer[] loaded = contractRepository.findById(contract.getId()).orElseThrow().getReminderDaysOverride();
        assertThat(loaded).containsExactly(30, 14, 7);
    }

    @Test
    void selectNewConstructorExpressionsLoadDocumentMeta() {
        var owner = userService.createUser(
                "hibernate7-select@cognologix.com", "Hibernate Seven", UserRole.ADMIN, "Secret123!", "test");
        var type = contractTypeRepository.findByTypeCodeIgnoreCase("MSA").orElseThrow();

        var contract = new Contract();
        contract.setContractNumber("H7-SELECT");
        contract.setTitle("Constructor expression");
        contract.setContractType(type);
        contract.setPaperType(PaperType.OWN);
        contract.setPartyName("Select Party");
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setOwnerUserId(owner.getId());
        contract.setCreatedBy("test");
        contractRepository.saveAndFlush(contract);

        var version = new ContractVersion();
        version.setContractId(contract.getId());
        version.setVersionNumber(1);
        version.setVersionLabel("v1");
        version.setStatus(VersionStatus.DRAFT);
        version.setUploadedBy("test");
        contractVersionRepository.saveAndFlush(version);

        var document = new ContractDocument();
        document.setContractVersionId(version.getId());
        document.setDocumentType(DocumentType.PRIMARY);
        document.setFilename("agreement.pdf");
        document.setContentType("application/pdf");
        document.setFileSizeBytes(12);
        document.setFileData(new byte[] {1, 2, 3});
        document.setUploadedBy("test");
        contractDocumentRepository.saveAndFlush(document);

        var template = new ContractTemplate();
        template.setContractType(type);
        template.setTemplateName("MSA template");
        template.setCreatedBy("test");
        contractTemplateRepository.saveAndFlush(template);

        var older = templateDocument(template.getId(), 1, "old.docx");
        var newer = templateDocument(template.getId(), 2, "new.docx");
        contractTemplateDocumentRepository.saveAndFlush(older);
        contractTemplateDocumentRepository.saveAndFlush(newer);
        entityManager.clear();

        List<DocumentMeta> documents = contractDocumentRepository.findMetaByVersionIds(List.of(version.getId()));
        assertThat(documents).singleElement().satisfies(meta -> {
            assertThat(meta.filename()).isEqualTo("agreement.pdf");
            assertThat(meta.documentType()).isEqualTo(DocumentType.PRIMARY);
            assertThat(meta.contractVersionId()).isEqualTo(version.getId());
        });

        List<TemplateDocumentMeta> latest = contractTemplateDocumentRepository.findLatestMeta(List.of(template.getId()));
        assertThat(latest).singleElement().satisfies(meta -> {
            assertThat(meta.filename()).isEqualTo("new.docx");
            assertThat(meta.versionNumber()).isEqualTo(2);
            assertThat(meta.templateId()).isEqualTo(template.getId());
        });
    }

    @Test
    void payrollGeneratedColumnsAreReadBack() {
        var period = new Period();
        period.setPeriodMonth(7);
        period.setPeriodYear(2026);
        periodRepository.saveAndFlush(period);

        var version = PeriodVersion.builder()
                .period(period)
                .versionNumber(1)
                .status(PeriodStatus.OPEN)
                .createdBy("test")
                .build();
        periodVersionRepository.saveAndFlush(version);

        var upload = SnapshotUpload.builder()
                .periodVersion(version)
                .importType(ImportType.ZOHO_PAYROLL)
                .uploadedBy("test")
                .originalFilename("payroll.xlsx")
                .rowCount(1)
                .build();
        snapshotUploadRepository.saveAndFlush(upload);

        var snapshot = PayrollSnapshot.builder()
                .snapshotUpload(upload)
                .periodVersion(version)
                .importType(ImportType.ZOHO_PAYROLL)
                .employeeNo("E-7")
                .fullName("Generated Columns")
                .grossPay(new BigDecimal("200.00"))
                .netPay(new BigDecimal("100.00"))
                .epfContribution(new BigDecimal("10.00"))
                .epsContribution(new BigDecimal("5.00"))
                .vpf(new BigDecimal("99.00"))
                .build();
        payrollSnapshotRepository.saveAndFlush(snapshot);

        var employee = EmployeeRegistry.builder()
                .employeeId("E-7")
                .fullName("Generated Columns")
                .build();
        employeeRegistryRepository.saveAndFlush(employee);

        var master = MasterRecord.builder()
                .periodVersion(version)
                .employeeRegistry(employee)
                .reconciliationStatus(ReconciliationStatus.MATCHED)
                .netPay(new BigDecimal("80.00"))
                .totalEmployerContributions(new BigDecimal("20.00"))
                .build();
        masterRecordRepository.saveAndFlush(master);
        entityManager.clear();

        PayrollSnapshot loadedSnapshot = payrollSnapshotRepository.findById(snapshot.getId()).orElseThrow();
        assertThat(loadedSnapshot.getTotalEmployerContributions()).isEqualByComparingTo("15.00");
        assertThat(loadedSnapshot.getTotalPayrollCost()).isEqualByComparingTo("115.00");

        MasterRecord loadedMaster = masterRecordRepository.findById(master.getId()).orElseThrow();
        assertThat(loadedMaster.getTotalPayrollCost()).isEqualByComparingTo("100.00");
    }

    private static ContractTemplateDocument templateDocument(java.util.UUID templateId, int version, String filename) {
        var document = new ContractTemplateDocument();
        document.setTemplateId(templateId);
        document.setVersionNumber(version);
        document.setFilename(filename);
        document.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        document.setFileSizeBytes(4);
        document.setFileData(new byte[] {4, 5, 6, 7});
        document.setUploadedBy("test");
        return document;
    }
}
