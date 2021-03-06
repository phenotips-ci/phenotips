/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 */
package org.phenotips.panels.internal;

import org.phenotips.data.Feature;
import org.phenotips.data.IndexedPatientData;
import org.phenotips.data.Patient;
import org.phenotips.data.PatientData;
import org.phenotips.vocabulary.Vocabulary;
import org.phenotips.vocabulary.VocabularyManager;
import org.phenotips.vocabulary.VocabularyTerm;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PatientDataAdapter}.
 */
public class PatientDataAdapterTest
{
    private static final String GENES = "genes";

    private static final String GLOBAL_QUALIFIERS = "global-qualifiers";

    private static final String POS_FEATURE_1_ID = "HP:1";

    private static final String POS_FEATURE_2_ID = "HP:2";

    private static final String NEG_FEATURE_1_ID = "-HP:1";

    private static final String GENE = "gene";

    private static final String STATUS = "status";

    private static final String HGNC = "hgnc";

    private static final String GENE_1_ID = "gene1";

    private static final String GENE_2_ID = "gene2";

    private static final String GENE_3_ID = "gene3";

    private static final String REJECTED_LABEL = "rejected";

    @Mock
    private Patient patient;

    @Mock
    private VocabularyManager vocabularyManager;

    @Mock
    private Vocabulary hgnc;

    @Mock
    private Feature positiveFeature1;

    @Mock
    private Feature positiveFeature2;

    @Mock
    private Feature negativeFeature1;

    @Mock
    private VocabularyTerm positiveTerm1;

    @Mock
    private VocabularyTerm positiveTerm2;

    @Mock
    private VocabularyTerm negativeTerm1;

    @Mock
    private VocabularyTerm qualifierTerm1;

    @Mock
    private VocabularyTerm qualifierTerm2;

    @Mock
    private VocabularyTerm geneTerm1;

    private PatientData<Object> geneData;

    private PatientData<Object> qualifierData;

    private PatientDataAdapter.AdapterBuilder adapterBuilder;

    private Set<Feature> features;

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);

        when(this.vocabularyManager.getVocabulary(HGNC)).thenReturn(this.hgnc);
        when(this.hgnc.getTerm(GENE_1_ID)).thenReturn(this.geneTerm1);
        when(this.hgnc.getTerm(GENE_2_ID)).thenReturn(null);

        final Map<String, String> geneDatum1 = new HashMap<>();
        geneDatum1.put(GENE, GENE_1_ID);
        geneDatum1.put(STATUS, REJECTED_LABEL);
        final Map<String, String> geneDatum2 = new HashMap<>();
        geneDatum2.put(GENE, GENE_2_ID);
        geneDatum2.put(STATUS, REJECTED_LABEL);
        final Map<String, String> geneDatum3 = new HashMap<>();
        geneDatum3.put(GENE, GENE_3_ID);
        geneDatum3.put(STATUS, "aa");
        this.geneData = new IndexedPatientData<>(GENES, Arrays.asList((Object) geneDatum1, geneDatum2, geneDatum3));
        when(this.patient.getData(GENES)).thenReturn(this.geneData);

        final List<VocabularyTerm> qualifierDatum1 = Collections.singletonList(this.qualifierTerm1);
        final List<VocabularyTerm> qualifierDatum2 = Collections.singletonList(this.qualifierTerm2);
        this.qualifierData = new IndexedPatientData<>(GLOBAL_QUALIFIERS, Arrays.asList((Object) qualifierDatum1,
            qualifierDatum2));
        when(this.patient.getData(GLOBAL_QUALIFIERS)).thenReturn(this.qualifierData);

        this.features = new HashSet<>();
        this.features.add(this.positiveFeature1);
        this.features.add(this.positiveFeature2);
        this.features.add(this.negativeFeature1);
        when(this.positiveFeature1.getValue()).thenReturn(POS_FEATURE_1_ID);
        when(this.positiveFeature2.getValue()).thenReturn(POS_FEATURE_2_ID);
        when(this.negativeFeature1.getValue()).thenReturn(NEG_FEATURE_1_ID);
        when(this.vocabularyManager.resolveTerm(POS_FEATURE_1_ID)).thenReturn(this.positiveTerm1);
        when(this.vocabularyManager.resolveTerm(POS_FEATURE_2_ID)).thenReturn(this.positiveTerm2);
        when(this.vocabularyManager.resolveTerm(NEG_FEATURE_1_ID)).thenReturn(this.negativeTerm1);
        when(this.positiveFeature1.isPresent()).thenReturn(true);
        when(this.positiveFeature2.isPresent()).thenReturn(true);
        when(this.negativeFeature1.isPresent()).thenReturn(false);
        when((Set<Feature>) this.patient.getFeatures()).thenReturn(this.features);

        this.adapterBuilder = new PatientDataAdapter.AdapterBuilder(this.patient, this.vocabularyManager);
    }

    @Test
    public void getPresentTermsReturnsEmptyCollectionWhenNoneStored()
    {
        when(this.patient.getFeatures()).thenReturn(null);
        when(this.patient.getData(GLOBAL_QUALIFIERS)).thenReturn(null);
        final PatientDataAdapter dataAdapter = this.adapterBuilder.build();
        Assert.assertTrue(dataAdapter.getPresentTerms().isEmpty());
    }

    @Test
    public void getPresentTermsReturnsTermsForAllPresentFeatures()
    {
        final PatientDataAdapter dataAdapter = this.adapterBuilder.build();
        final Set<VocabularyTerm> presentTerms = new HashSet<>();
        presentTerms.add(this.positiveTerm1);
        presentTerms.add(this.positiveTerm2);
        presentTerms.add(this.qualifierTerm1);
        presentTerms.add(this.qualifierTerm2);
        Assert.assertEquals(presentTerms, dataAdapter.getPresentTerms());
    }

    @Test
    public void getAbsentTermsReturnsEmptyCollectionWhenNoneStored()
    {
        when(this.patient.getFeatures()).thenReturn(null);
        when(this.patient.getData(GLOBAL_QUALIFIERS)).thenReturn(null);
        final PatientDataAdapter dataAdapter = this.adapterBuilder.build();
        Assert.assertTrue(dataAdapter.getAbsentTerms().isEmpty());
    }

    @Test
    public void getAbsentTermsReturnsTermsForAllAbsentFeatures()
    {
        final PatientDataAdapter dataAdapter = this.adapterBuilder.build();
        final Set<VocabularyTerm> absentTerms = new HashSet<>();
        absentTerms.add(this.negativeTerm1);
        Assert.assertEquals(absentTerms, dataAdapter.getAbsentTerms());
    }

    @Test
    public void getRejectedGenesReturnsEmptyCollectionWhenRejectedGenesNotRetrievedFromPatient()
    {
        when(this.patient.getFeatures()).thenReturn(null);
        when(this.patient.getData(GLOBAL_QUALIFIERS)).thenReturn(null);
        final PatientDataAdapter dataAdapter = this.adapterBuilder.build();
        Assert.assertTrue(dataAdapter.getRejectedGenes().isEmpty());
    }

    @Test
    public void getRejectedGenesReturnsEmptyCollectionWhenNoRejectedGenesStored()
    {
        when(this.patient.getFeatures()).thenReturn(null);
        when(this.patient.getData(GLOBAL_QUALIFIERS)).thenReturn(null);
        when(this.patient.getData(GENES)).thenReturn(null);
        final PatientDataAdapter dataAdapter = this.adapterBuilder.withRejectedGenes().build();
        Assert.assertTrue(dataAdapter.getRejectedGenes().isEmpty());
    }

    @Test
    public void getRejectedGenesReturnsTermsForAllNegativeGenes()
    {
        final PatientDataAdapter dataAdapter = this.adapterBuilder.withRejectedGenes().build();
        final Set<VocabularyTerm> rejectedGenes = new HashSet<>();
        rejectedGenes.add(this.geneTerm1);
        Assert.assertEquals(rejectedGenes, dataAdapter.getRejectedGenes());
    }
}
