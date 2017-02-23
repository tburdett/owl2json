#!/bin/sh

base="$(dirname "$(readlink -f "$0")")"/..;
current=`pwd`;
java=java;
#args="-Dorg.xml.sax.driver=uk.ac.ebi.fgpt.zooma.xml.ZoomaXMLReaderProxy -Xmx2g -DentityExpansionLimit=1000000000";
args="-DentityExpansionLimit=1000000000";

for file in `ls $base/lib`
do
  jars=$jars:$base/lib/$file;
done

classpath="$base/config$jars";

$java $args -classpath $classpath uk.ac.ebi.fgpt.owl2json.OWL2JSONDriver $@ 2>&1;
exit $?;
