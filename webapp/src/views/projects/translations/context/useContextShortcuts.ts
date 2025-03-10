import { useCallback, useRef } from 'react';
import { useContextSelector } from 'use-context-selector';

import { translationStates } from 'tg.constants/translationStates';
import { getEventAction } from 'tg.fixtures/shortcuts';
import {
  ARROWS,
  getCurrentlyFocused,
  Position,
  translationsNavigator,
} from './tools';
import {
  TranslationsContext,
  useTranslationsDispatch,
} from './TranslationsContext';
import { useProjectPermissions } from 'tg.hooks/useProjectPermissions';
import { ProjectPermissionType } from 'tg.service/response.types';

export const KEY_MAP = {
  MOVE: ARROWS,
  EDIT: ['Enter'],
  CANCEL: ['Escape'],
  CHANGE_STATE: ['Action+E'],
} as const;

export type ShortcutsArrayType = [
  action: string,
  shortcut: string[] | ReadonlyArray<string>
];

export const useContextShortcuts = () => {
  const root = document.getElementById('root');
  const onKeyRef = useRef<(e: KeyboardEvent) => void>();
  const availableActions = useRef<() => ShortcutsArrayType[]>();
  const dispatch = useTranslationsDispatch();
  const cursor = useContextSelector(TranslationsContext, (c) => c.cursor);
  const view = useContextSelector(TranslationsContext, (c) => c.view);
  const permissions = useProjectPermissions();
  const elementsRef = useContextSelector(
    TranslationsContext,
    (c) => c.elementsRef
  );
  const fixedTranslations = useContextSelector(
    TranslationsContext,
    (c) => c.translations
  );
  const languages = useContextSelector(
    TranslationsContext,
    (c) => c.selectedLanguages
  );
  const list = useContextSelector(TranslationsContext, (c) => c.reactList);

  const hasCorrectTarget = (target: Element) =>
    target === document.body || root?.contains(target);

  const canEdit = permissions.satisfiesPermission(ProjectPermissionType.EDIT);
  const canTranslate = permissions.satisfiesPermission(
    ProjectPermissionType.TRANSLATE
  );

  const isTranslation = (position: Position | undefined) => position?.language;

  const getMoveHandler = () => {
    return (e: KeyboardEvent) => {
      e.preventDefault();
      const navigator = translationsNavigator(
        fixedTranslations,
        languages,
        elementsRef.current,
        view,
        list?.getVisibleRange.bind(list)()
      );
      const nextLocation = navigator.getNextLocation(e.key as any);
      if (nextLocation) {
        const el = elementsRef.current?.get(
          JSON.stringify({
            keyId: nextLocation.keyId,
            language: nextLocation.language,
          })
        );
        if (el !== document.activeElement) {
          el?.focus();
          el?.scrollIntoView({
            block: 'center',
            inline: 'center',
            behavior: 'smooth',
          });
        }
      }
    };
  };

  const getEnterHandler = () => {
    const focused = getCurrentlyFocused(elementsRef.current);
    if (focused) {
      if (isTranslation(focused) ? canTranslate : canEdit)
        return (e: KeyboardEvent) => {
          e.preventDefault();
          dispatch({
            type: 'SET_EDIT',
            payload: {
              keyId: focused.keyId,
              language: focused.language,
              mode: 'editor',
            },
          });
        };
    }
  };

  const getCancelHandler = () => {
    const focused = getCurrentlyFocused(elementsRef.current);
    if (focused) {
      // @ts-ignore
      return (e: KeyboardEvent) => document.activeElement.blur?.();
    }
  };

  const getChangeStateHandler = () => {
    const focused = getCurrentlyFocused(elementsRef.current);

    if (focused?.language && canTranslate) {
      const translation = fixedTranslations?.find(
        (t) => t.keyId === focused.keyId
      )?.translations[focused.language];

      const newState =
        translation?.state && translationStates[translation.state].next[0];

      if (translation && newState) {
        return (e: KeyboardEvent) => {
          e.preventDefault();
          dispatch({
            type: 'SET_TRANSLATION_STATE',
            payload: {
              keyId: focused.keyId,
              language: focused.language as string,
              translationId: translation.id,
              state: newState,
            },
          });
        };
      }
    }

    // trigger 'preventDefault' even if handler is not active
    return true;
  };

  const getHandler = (
    action: keyof typeof KEY_MAP
  ): ((e: KeyboardEvent) => void) | undefined | true => {
    switch (action) {
      case 'MOVE':
        return getMoveHandler();
      case 'EDIT':
        return getEnterHandler();
      case 'CANCEL':
        return getCancelHandler();
      case 'CHANGE_STATE':
        return getChangeStateHandler();
    }
  };

  const isStateCorrect = () => {
    const activeElement = document.activeElement || document.body;

    // check if events are not coming from popup
    if (!hasCorrectTarget(activeElement)) {
      return false;
    }

    // if editor is open, don't apply shortcuts
    if (cursor) {
      const current = getCurrentlyFocused(elementsRef.current);
      if (
        current?.keyId === cursor.keyId &&
        current?.language === cursor.language
      ) {
        return false;
      }
    }

    // ignore events coming from inputs
    // as we don't want to influence them
    if (
      activeElement.tagName === 'INPUT' &&
      // @ts-ignore
      !['checkbox', 'radio', 'submit', 'reset'].includes(activeElement.type)
    ) {
      return false;
    }

    return true;
  };

  onKeyRef.current = (e: KeyboardEvent) => {
    const action = getEventAction(e, KEY_MAP);

    if (action) {
      const handler = getHandler(action);
      if (handler === true) {
        e.preventDefault();
      } else if (typeof handler === 'function' && isStateCorrect()) {
        handler(e);
      }
    }
  };

  availableActions.current = () => {
    if (!isStateCorrect()) {
      return [];
    }
    return Object.entries(KEY_MAP).filter(
      ([action]) => typeof getHandler(action as any) === 'function'
    );
  };

  const getAvailableActions = useCallback(
    () => availableActions.current?.() || [],
    [availableActions]
  );

  const onKey = useCallback(
    (e: KeyboardEvent) => onKeyRef.current?.(e),
    [onKeyRef]
  );

  return { getAvailableActions, onKey };
};
