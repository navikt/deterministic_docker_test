package no.nav.helse.testgreier

import generated.Grunndata
import java.io.StringReader
import java.nio.charset.Charset
import javax.xml.bind.JAXB

class TestNoeXmlGreier {

    private val fakeResponse = TestNoeXmlGreier::class.java.getResourceAsStream("/hentRollerResponse.xml")
        .readAllBytes().toString(Charset.forName("UTF-8"))
    val grunndata:Grunndata = JAXB.unmarshal(StringReader(fakeResponse), Grunndata::class.java)

}