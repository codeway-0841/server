import {
  cleanTranslationFiltersData,
  createTranslationFiltersData,
  login,
} from '../../common/apiCalls';
import { ProjectDTO } from '../../../../webapp/src/service/response.types';
import { visitTranslations } from '../../common/translations';
import { gcy, getPopover, selectInSelect } from '../../common/shared';
import { waitForGlobalLoading } from '../../common/loading';

describe('Translations Base', () => {
  let project: ProjectDTO = null;

  before(() => {
    cleanTranslationFiltersData();
    createTranslationFiltersData()
      .then((p) => {
        project = p;
      })
      .then(() => {
        login('franta', 'admin');
        visit();
        cy.contains('Translations').should('be.visible');
        waitForGlobalLoading();
      });
  });

  beforeEach(() => {
    login('franta', 'admin');
    visit();
    cy.contains('Translations').should('be.visible');
    waitForGlobalLoading();
  });

  after(() => {
    cleanTranslationFiltersData();
  });

  it(`filters work correctly`, () => {
    [
      {
        filterOption: 'At least one translated',
        toMissAfter: ['key with screenshot'],
        toSeeAfter: ['A key'],
        only: false,
      },
      {
        filterOption: 'Missing translation',
        toMissAfter: [],
        toSeeAfter: ['A key', 'key with screenshot'],
        only: false,
      },
      {
        filterOption: 'With screenshots',
        toMissAfter: ['A key'],
        toSeeAfter: ['key with screenshot', 'key with screenshot 2'],
        only: false,
      },
      {
        filterOption: 'No screenshots',
        toMissAfter: ['key with screenshot', 'key with screenshot 2'],
        toSeeAfter: ['A key'],
        only: false,
      },
    ].forEach((test) => {
      assertFilter(test.filterOption, test.toMissAfter, test.toSeeAfter);
    });
  });

  it('filter exclusiveness', () => {
    gcy('translations-filter-select').click();
    [
      ['No screenshots', 'With screenshots'],
      ['At least one translated', 'Missing translation'],
    ].forEach((pair) => {
      cy.contains(pair[0]).click();
      cy.contains(pair[1]).click();

      gcy('translations-filter-option')
        .contains(pair[0])
        .closest('li')
        .find('input')
        .should('not.checked');
    });
  });

  it('filters by state', () => {
    [
      {
        state: ['Untranslated'],
        toMissAfter: ['state test key 2'],
        toSeeAfter: ['key with screenshot'],
      },
      {
        state: ['Translated'],
        toMissAfter: ['state test key 4'],
        toSeeAfter: ['Z key'],
      },
      {
        state: ['Machine translated'],
        toMissAfter: ['state test key 4'],
        toSeeAfter: ['state test key 5'],
      },
      {
        state: ['Reviewed'],
        toMissAfter: ['state test key 4'],
        toSeeAfter: ['state test key 2'],
      },
      {
        state: ['Translated', 'Machine translated'],
        toMissAfter: ['state test key 2', 'state test key 4'],
        toSeeAfter: ['state test key 5', 'Z key'],
      },
    ].forEach((test) => {
      assertStateFilter(test.state, test.toMissAfter, test.toSeeAfter);
    });
  });

  const assertStateFilter = (
    states: string[],
    toMissAfter: string[],
    toSeeAfter: string[]
  ) => {
    toMissAfter.forEach((i) => cy.contains(i).should('exist'));
    selectInSelect(gcy('translations-filter-select'), 'English');
    states.forEach((state) => {
      getPopover().contains(state).click();
    });
    cy.focused().type('{Esc}');
    cy.focused().type('{Esc}');
    toSeeAfter.forEach((i) => cy.contains(i).should('exist'));
    toMissAfter.forEach((i) => cy.contains(i).should('not.exist'));
    gcy('translations-filter-clear-all').click();
  };

  const assertFilter = (
    filterOption: string,
    toMissAfter: string[],
    toSeeAfter: string[]
  ) => {
    toMissAfter.forEach((i) => cy.contains(i).should('be.visible'));
    selectInSelect(gcy('translations-filter-select'), filterOption);
    cy.focused().type('{Esc}');
    toSeeAfter.forEach((i) => cy.contains(i).should('be.visible'));
    toMissAfter.forEach((i) => cy.contains(i).should('not.exist'));
    selectInSelect(gcy('translations-filter-select'), filterOption);
    cy.focused().type('{Esc}');
  };

  const visit = () => {
    visitTranslations(project.id);
  };
});
