import React from 'react';
import { T } from '@tolgee/react';
import { Button, makeStyles } from '@material-ui/core';
import { CameraAlt } from '@material-ui/icons';
import { useContextSelector } from 'use-context-selector';

import { StateType } from 'tg.constants/translationStates';
import { ControlsButton } from './ControlsButton';
import { StateTransitionButtons } from './StateTransitionButtons';
import { TranslationsContext } from '../context/TranslationsContext';
import LoadingButton from 'tg.component/common/form/LoadingButton';

const useStyles = makeStyles((theme) => ({
  leftPart: {
    display: 'flex',
    alignItems: 'flex-start',
    overflow: 'hidden',
    padding: theme.spacing(1, 1.5, 1.5, 1.5),
    '& > * + *': {
      marginLeft: 10,
    },
  },
  rightPart: {
    display: 'flex',
    alignItems: 'center',
    padding: theme.spacing(1, 1.5, 1.5, 0),
  },
}));

type ControlsProps = {
  state?: StateType;
  onSave?: () => void;
  onCancel?: () => void;
  onScreenshots?: () => void;
  onStateChange?: (state: StateType) => void;
  screenshotRef?: React.Ref<any>;
  screenshotsPresent?: boolean;
};

export const ControlsEditor: React.FC<ControlsProps> = ({
  state,
  onSave,
  onCancel,
  onScreenshots,
  onStateChange,
  screenshotRef,
  screenshotsPresent,
}) => {
  const classes = useStyles();

  // right section
  const displayTransitionButtons = state;
  const displayScreenshots = onScreenshots;
  const displayRightPart = displayTransitionButtons || displayScreenshots;

  const isLoading = useContextSelector(
    TranslationsContext,
    (c) => c.isEditLoading
  );

  return (
    <>
      <div className={classes.leftPart}>
        <Button
          onClick={onCancel}
          color="primary"
          variant="outlined"
          size="small"
          data-cy="translations-cell-cancel-button"
        >
          <T>translations_cell_cancel</T>
        </Button>
        <LoadingButton
          onClick={onSave}
          color="primary"
          variant="contained"
          size="small"
          loading={isLoading}
          data-cy="translations-cell-save-button"
        >
          <T>translations_cell_save</T>
        </LoadingButton>
      </div>

      {displayRightPart && (
        <div className={classes.rightPart}>
          {displayTransitionButtons && (
            <StateTransitionButtons
              state={state}
              onStateChange={onStateChange}
            />
          )}
          {displayScreenshots && (
            <ControlsButton
              onClick={onScreenshots}
              ref={screenshotRef}
              tooltip={<T>translations_screenshots_tooltip</T>}
              data-cy="translations-cell-screenshots-button"
            >
              <CameraAlt
                fontSize="small"
                color={screenshotsPresent ? 'primary' : undefined}
              />
            </ControlsButton>
          )}
        </div>
      )}
    </>
  );
};
