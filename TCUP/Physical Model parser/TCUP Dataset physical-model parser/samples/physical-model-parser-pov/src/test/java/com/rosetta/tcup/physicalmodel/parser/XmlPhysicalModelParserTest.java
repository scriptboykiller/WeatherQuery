package com.rosetta.tcup.physicalmodel.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rosetta.tcup.physicalmodel.support.InvalidPhysicalModelInputException;
import org.junit.jupiter.api.Test;

class XmlPhysicalModelParserTest {
    private final XmlPhysicalModelParser parser = new XmlPhysicalModelParser();

    @Test
    void preservesRootIncludesAttributesAndInfersVisibleRepetition() {
        var result = parser.parse("""
                <cfg:employees xmlns:cfg="urn:test" batch="1">
                    <cfg:employee id="1001"><cfg:name>Ava</cfg:name></cfg:employee>
                    <cfg:employee id="1002"><cfg:name>Jerry</cfg:name></cfg:employee>
                </cfg:employees>""");

        assertThat(result.attributes()).extracting(attribute -> attribute.path())
                .containsExactly("employees.@batch", "employees.employee[].@id", "employees.employee[].name");
    }

    @Test
    void blocksDoctypeAndExternalEntities() {
        assertThatThrownBy(() -> parser.parse("""
                <!DOCTYPE employee [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <employee><name>&secret;</name></employee>"""))
                .isInstanceOf(InvalidPhysicalModelInputException.class);
    }
}
