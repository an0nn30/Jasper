package dev.jasper.app.appearance;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Literal inventory ledger: adding a requirement or losing its actual owner sample fails independently of resolver validation. */
final class DesktopPaintCoverage {
    private DesktopPaintCoverage() { }
    record Entry(String requirement,String classification,String standard,String classic,String reason) {
        String sample(String kit) {return kit.equals("classic")?classic:standard;}
    }
    static List<Entry> entries() throws Exception {
        try(var input=DesktopPaintCoverage.class.getResourceAsStream("desktop-render-coverage.tsv")) {
            assertThat(input).isNotNull();
            var rows=new String(input.readAllBytes(),StandardCharsets.UTF_8).lines().skip(1).map(line->{
                var cells=line.split("\t",-1);assertThat(cells).hasSize(5);
                return new Entry(cells[0],cells[1],cells[2],cells[3],cells[4]);
            }).toList();
            assertThat(rows).extracting(Entry::requirement).containsExactlyElementsOf(DesktopPaintPairs.all().stream().map(DesktopPaintPairs.Requirement::name).toList());
            for(var entry:rows) {
                assertThat(entry.reason()).as(entry.requirement()).isNotBlank();
                assertThat(entry.classification()).isIn("rendered","unused-role-cross-product","unused-state-cross-product","parent-cross-product","gradient-endpoint","native-acceptance-only");
                if(entry.classification().equals("rendered"))assertThat(List.of(entry.standard(),entry.classic())).as(entry.requirement()+" has an owner").isNotEqualTo(List.of("-","-"));
                else assertThat(List.of(entry.standard(),entry.classic())).as(entry.requirement()+" cannot be counted as rendered").containsExactly("-","-");
            }
            return rows;
        }
    }
    static int verify(String kit,boolean high,List<DesktopOwnerPaints.Sample> samples,Path report) throws Exception {
        var measured=new LinkedHashMap<String,DesktopOwnerPaints.Sample>();
        for(var sample:samples)assertThat(measured.put(sample.key(),sample)).as("unique actual owner state "+sample.key()).isNull();
        var requirements=new LinkedHashMap<String,DesktopPaintPairs.Requirement>();
        DesktopPaintPairs.all().forEach(pair->requirements.put(pair.name(),pair));
        var lines=new ArrayList<String>();lines.add("requirement\tclassification\towner-state\tink\tbackground\tratio\tminimum\treason");int rendered=0;
        for(var entry:entries()) {
            String key=entry.sample(kit);
            if(entry.classification().equals("rendered")&&!key.equals("-")) {
                var sample=measured.get(key);assertThat(sample).as(entry.requirement()+" requires actual "+kit+" pixels from "+key).isNotNull();
                var pair=requirements.get(entry.requirement());
                assertThat(sample.inkRole()).as(entry.requirement()).isEqualTo(pair.ink().key());
                assertThat(sample.backgroundRole()).as(entry.requirement()).isEqualTo(pair.surface().key());
                assertThat(sample.ratio()).as(entry.requirement()+" measured "+key).isGreaterThanOrEqualTo(pair.minimum(high));
                lines.add(String.format(Locale.ROOT,"%s\trendered\t%s\t%08x\t%08x\t%.8f\t%.1f\t%s",entry.requirement(),key,sample.ink(),sample.background(),sample.ratio(),pair.minimum(high),entry.reason()));
                rendered++;
            } else {
                String kind=entry.classification().equals("rendered")?"other-kit-owner":entry.classification();
                lines.add(entry.requirement()+"\t"+kind+"\t-\t-\t-\t-\t-\t"+entry.reason());
            }
        }
        Files.write(report,lines);return rendered;
    }
}
