package com.rosetta.tcup.physicalmodel.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class XsdPhysicalModelParserTest {
    private final XsdPhysicalModelParser parser = new XsdPhysicalModelParser();

    @Test
    void supportsInlineTypesNamedTypesAndReferenceUsageOccurrences() {
        var result = parser.parse("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="EmployeeType"><xs:sequence>
                    <xs:element name="id" type="xs:integer"/><xs:element name="name" type="xs:string"/>
                  </xs:sequence></xs:complexType>
                  <xs:element name="owner" type="xs:string"/>
                  <xs:element name="appConfig"><xs:complexType><xs:sequence>
                    <xs:element name="employee" type="EmployeeType"/>
                    <xs:element ref="owner" maxOccurs="unbounded"/>
                  </xs:sequence></xs:complexType></xs:element>
                </xs:schema>""");

        assertThat(result.attributes()).extracting(attribute -> attribute.path())
                .containsExactly("appConfig.employee.id", "appConfig.employee.name", "appConfig.owner[]");
    }

    @Test
    void returnsPartialResultsForMissingAndRecursiveTypes() {
        var result = parser.parse("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="NodeType"><xs:sequence>
                    <xs:element name="name" type="xs:string"/><xs:element name="child" type="NodeType"/>
                  </xs:sequence></xs:complexType>
                  <xs:element name="node" type="NodeType"/>
                  <xs:element name="code" type="xs:integer"/><xs:element name="bad" type="MissingType"/>
                </xs:schema>""");

        assertThat(result.attributes()).extracting(attribute -> attribute.path()).contains("node.name", "code");
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("Recursive"));
        assertThat(result.warnings()).anyMatch(warning -> warning.contains("MissingType"));
    }

    @Test
    void extractsAttributesFromNamedComplexTypesWithoutChildElements() {
        var result = parser.parse("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="LeaveType">
                    <xs:attribute name="type" type="xs:string" use="required"/>
                    <xs:attribute name="name" type="xs:string" use="required"/>
                    <xs:attribute name="defaultDays" type="xs:int"/>
                  </xs:complexType>
                  <xs:complexType name="LeaveListType"><xs:sequence>
                    <xs:element name="leave" type="LeaveType" maxOccurs="unbounded"/>
                  </xs:sequence></xs:complexType>
                  <xs:element name="company"><xs:complexType><xs:sequence>
                    <xs:element name="leaveTypeConfig" type="LeaveListType"/>
                  </xs:sequence><xs:attribute name="companyCode" type="xs:string"/>
                  </xs:complexType></xs:element>
                </xs:schema>""");

        assertThat(result.attributes()).extracting(attribute -> attribute.path()).containsExactly(
                "company.leaveTypeConfig.leave[].@type",
                "company.leaveTypeConfig.leave[].@name",
                "company.leaveTypeConfig.leave[].@defaultDays",
                "company.@companyCode");
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void extractsSimpleContentValueAndExtensionAttributes() {
        var result = parser.parse("""
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:complexType name="LoggerType"><xs:simpleContent>
                    <xs:extension base="xs:string"><xs:attribute name="name" type="xs:string" use="required"/>
                    </xs:extension>
                  </xs:simpleContent></xs:complexType>
                  <xs:complexType name="LogLevelType"><xs:sequence>
                    <xs:element name="logger" type="LoggerType" maxOccurs="unbounded"/>
                  </xs:sequence></xs:complexType>
                  <xs:element name="gatewayConfig"><xs:complexType><xs:sequence>
                    <xs:element name="logLevel" type="LogLevelType"/>
                  </xs:sequence></xs:complexType></xs:element>
                </xs:schema>""");

        assertThat(result.attributes()).extracting(attribute -> attribute.path()).containsExactly(
                "gatewayConfig.logLevel.logger[]",
                "gatewayConfig.logLevel.logger[].@name");
        assertThat(result.warnings()).isEmpty();
    }
}
